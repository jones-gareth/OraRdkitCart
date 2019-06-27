package com.cairn.rmi.index;

import com.cairn.common.*;
import com.cairn.rmi.TaskException;
import com.cairn.rmi.server.DatabaseObject;
import com.cairn.rmi.server.Util;
import com.cairn.rmi.server.TaskJobResults;
import com.cairn.rmi.server.TaskManagerImpl;
import com.cairn.rmi.server.TaskUtil;
import com.cairn.rmi.util.ROMolContainer;
import oracle.jdbc.OraclePreparedStatement;
import oracle.jdbc.OracleResultSet;
import oracle.sql.ROWID;
import org.RDKit.ExplicitBitVect;
import org.RDKit.RDKFuncs;
import org.RDKit.ROMol;
import org.RDKit.SparseIntVectu32;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Builds in-memory fingerprint indexes hashed by ROWID for fast external
 * substructure and similarity searching.
 *
 * @author Gareth Jones
 */
public class TableIndex extends IndexBase {
    // set local index to save the lookup in a local directory instead of Oracle
    private static final boolean LOCAL_INDEX = true;
    // the filename used to store the local index
    private static final String INDEX_FILE = "table_index.bin";

    private static final Logger logger = Logger.getLogger(TableIndex.class);
    // Stores binary fingerprint and smiles by ROWID
    private volatile Map<RowKey, FingerprintedSmiles> lookup;

    // index info for local cache
    private static class TableIndexInfo implements Serializable {
        private static final long serialVersionUID = 1000L;
        private final List<RDKitOps.ExtendedFingerPrintType> fingerPrintTypes = new ArrayList<>();
        private volatile String cacheName;
    }

    private volatile TableIndexInfo indexInfo = new TableIndexInfo();

    /**
     * @param ownerName
     * @param tableName
     */
    public TableIndex(String ownerName, String tableName, String columnName)
            throws TaskException {
        super(ownerName, tableName, columnName);
        identifyLogTable();
    }

    /*
     * (non-Javadoc)
     *
     * @see com.cairn.rmi.index.IndexBase#buildIndex()
     */
    @Override
    public synchronized void buildIndex() throws TaskException {
        boolean useIndexBuildPool = IndexBuildPool.isUseIndexBuildPool();

        IndexBuildPool indexBuildPool = null;
        int jobNo = 0;
        if (useIndexBuildPool) {
            indexBuildPool = IndexBuildPool.getInstance();
            jobNo = indexBuildPool.startJob(this);
        }

        logger.info("Building index for " + fullSchemaName());
        String query = "select rowid, " + columnName + " from " + ownerName + "."
                + tableName;
        logger.debug("Query is " + query);

        try (var connection = getConnection();
             PreparedStatement preparedStatement = SqlUtil.getOracleConnection(connection)
                     .prepareStatement(query);
             OracleResultSet resultSet = (OracleResultSet) preparedStatement
                     .executeQuery()) {

            StopWatch stopWatch = new StopWatch();
            stopWatch.start();
            lookup = new ConcurrentHashMap<>();

            int count = 0;

            while (resultSet.next()) {

                RowKey rowId = new RowKey(resultSet.getROWID(1));
                Object rowValue = retrieveRowValueFromDatabase(2, resultSet);

                if (rowValue == null)
                    continue;

                if (useIndexBuildPool) {
                    indexBuildPool.submitRow(jobNo, rowId, rowValue);
                    count++;
                } else {
                    boolean added = createEntry(rowId, rowValue);

                    if (added)
                        count++;
                    if (count % 10000 == 0) {
                        logger.info("loaded " + count + " structures");
                        Util.printMemoryUsage(logger);
                    }
                }
            }

            if (useIndexBuildPool)
                indexBuildPool.finishJob(jobNo);
            Util.printMemoryUsage(logger);
            stopWatch.stop();
            logger.info("Took " + stopWatch.getTime() / 1000.0
                    + " seconds to build index");
            processLogTable();
            saveIndex();
            setIndexLoaded(true);
        } catch (SQLException e) {
            String message = "SQL error building index";
            logger.error(message, e);
            throw new RuntimeException(message);
        }
    }


    /**
     * Perform similarity search using de Morgan fingerprints. Adds results to a batch queue.
     *
     * @param jobNo
     * @param fingerprintType
     * @param searchMethod
     * @param smiles
     * @param cutoff
     * @param maxHits
     * @param alpha
     * @param beta
     * @throws TaskException
     */
    public void extendedSimilaritySearch(int jobNo, String fingerprintType, String searchMethod,
                                         String smiles, double cutoff, int maxHits, Double alpha, Double beta)
            throws TaskException {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        processLogTable();
        if (indexInfo.fingerPrintTypes.isEmpty())
            throw new TaskException("No extended fingerprints present in index!");

        var fpType = RDKitOps.ExtendedFingerPrintType.valueOf(fingerprintType.toUpperCase());
        var indexOpt = IntStream.range(0, indexInfo.fingerPrintTypes.size())
                .filter(i -> indexInfo.fingerPrintTypes.get(i) == fpType)
                .findFirst();
        if (indexOpt.isEmpty())
            throw new TaskException("Fingerprint type " + fpType + " is not present in index");
        var fpIndex = indexOpt.getAsInt();

        if (cutoff > 1.0 || cutoff < 0.0)
            throw new IllegalArgumentException("Invalid similarity cutoff" + cutoff);

        var queryOpt = RDKitOps.smilesToMol(smiles);
        if (queryOpt.isEmpty()) {
            logger.warn("query smiles " + smiles + " is not valid");
            return;
        }
        var query = queryOpt.get();

        var queryFingerprint = fpType.createFingerprint(query);
        var nHits = 0;
        TaskJobResults taskJobResults = new TaskJobResults(jobNo, true);

        for (var entry : lookup.entrySet()) {
            var rowKey = entry.getKey();
            var rowValue = entry.getValue();

            var fp = rowValue.getDeMorganFingerprints().get(fpIndex);
            if (fp == null)
                continue;

            double similarity;
            switch (searchMethod.toLowerCase()) {
                case "tanimoto":
                    similarity = RDKFuncs.TanimotoSimilaritySIVu32(queryFingerprint, fp);
                    break;
                case "dice":
                    similarity = RDKFuncs.DiceSimilarity(queryFingerprint, fp);
                    break;
                case "tversky":
                    if (alpha == null || beta == null)
                        throw new TaskException("Tversky similarity: alpha and beta not set");
                    similarity = RDKFuncs.TverskySimilarity(queryFingerprint, fp, alpha, beta);
                    break;
                default:
                    throw new TaskException("Unknown similarity search method " + searchMethod);
            }

            if (similarity >= cutoff) {
                taskJobResults.addHit(rowKey.getRowId(), similarity);
                nHits++;
                if (maxHits > 0 && nHits >= maxHits)
                    break;
                if (logger.isDebugEnabled()) {
                    logger.debug("Got hit against index " + nHits + " similarity "
                            + similarity + " row " + (new String(rowKey.getRowId())) + " smiles "
                            + rowValue.getSmiles());
                }
            }
        }

        taskJobResults.finish();
        queryFingerprint.delete();
        stopWatch.stop();
        logger.info("Searched fingerprint database " + stopWatch.getTime() / 1000.0
                + " seconds, got " + nHits + " hits");
    }


    /**
     * Converts index creation parameter string into fingerprint types. This
     * should only be called just before the build method.
     *
     * @param params
     * @throws TaskException
     */
    public void createFingerprintTypes(String params) {
        if (StringUtils.isBlank(params))
            return;
        params = params.toUpperCase();
        indexInfo.fingerPrintTypes.clear();
        Arrays.stream(params.split("\\s+"))
                .map(String::toUpperCase)
                .filter(s -> s.startsWith("FP="))
                .map(s -> s.substring(3))
                .map(RDKitOps.ExtendedFingerPrintType::valueOf)
                .forEach(indexInfo.fingerPrintTypes::add);
    }


    /**
     * Adds a row value and rowId to the in-memory index.
     *
     * @param rowId
     * @param rowValue
     * @return
     */
    boolean createEntry(RowKey rowId, Object rowValue) {

        boolean traceEnabled = logger.isTraceEnabled();
        if (traceEnabled) {
            logger.trace("createEntry: About to create index entry for rowId " + rowId
                    + " rowValue " + rowValue);
        }

        boolean rtn = false;
        if (rowValue == null)
            return false;
        switch (indexColumnType) {
            case SMILES:
                String smiles = (String) rowValue;
                if (StringUtils.isNotEmpty(smiles)) {
                    rtn = createEntryFromSmiles(rowId, smiles);
                }
                break;
            case SDF:
                String sdf = (String) rowValue;
                if (StringUtils.isNotEmpty(sdf)) {
                    rtn = createEntryFromSdf(rowId, sdf);
                }
                break;
            case BINARY:
                byte[] molData = (byte[]) rowValue;
                var molContainer = (ROMolContainer) Util.byteArrayToObject(molData);
                rtn = createEntryFromMol(rowId, molContainer.getMol());
                break;
            default:
                throw new IllegalArgumentException("Unknown index type " + indexColumnType);
        }

        if (traceEnabled) {
            logger.trace("createEntry: returning " + rtn + " for rowId " + rowId
                    + " rowValue " + rowValue);
        }
        return rtn;

    }

    /*
     * (non-Javadoc)
     *
     * @see com.cairn.rmi.index.IndexBase#truncate()
     */
    @Override
    public synchronized void truncate() throws TaskException {
        logger.info("Truncating index for " + fullSchemaName());
        lookup = new ConcurrentHashMap<>();
        setIndexLoaded(true);
        saveIndex();
    }

    /**
     * Adds fingerprint and canonicalized smiles to the index for a smiles
     * entry.
     *
     * @param rowId
     * @param smiles smiles prior to canonicalization.
     * @return false if we can't parse the smiles.
     */
    private boolean createEntryFromSmiles(RowKey rowId, String smiles) {
        if (smiles.contains(">>"))
            logger.debug("Converting reaction smiles to mixture");
        smiles = smiles.replace(">>", ".");
        var molOpt = RDKitOps.smilesToMol(smiles);
        if (molOpt.isEmpty()) {
            logger.warn(fullSchemaName() + " : " + rowId.toString() + " bad smiles : "
                    + smiles);
            return false;
        }
        var mol = molOpt.get();
        // canonicalize smiles, if required
        var cansmi = RDKitOps.canonicalize(mol);

        boolean ok = createEntryCommon(cansmi, rowId, mol);
        if (!ok) {
            logger.warn(fullSchemaName() + " : " + rowId.toString()
                    + " failed to fingerprint : " + smiles + "[canonicalized to "
                    + cansmi + "]");
        }

        mol.delete();
        return ok;
    }

    /**
     * Creates a fingerprint for a smiles and adds it to the lookup
     *
     * @param smi
     * @param rowId
     * @param mol
     * @return
     */
    private boolean createEntryCommon(String smi, RowKey rowId, ROMol mol) {

        var patternFp = RDKitOps.patternFingerPrintMol(mol);
        if (patternFp == null) {
            return false;
        }
        List<SparseIntVectu32> deMorganFingerprints = null;
        if (!indexInfo.fingerPrintTypes.isEmpty()) {
            deMorganFingerprints = indexInfo.fingerPrintTypes.stream()
                    .map(t -> t.createFingerprint(mol))
                    .collect(Collectors.toList());
        }

        // add to hash
        var fingerprint = RDKitOps.explictBitVectToBitSet(patternFp);
        var value = new FingerprintedSmiles(smi, fingerprint, deMorganFingerprints);
        lookup.put(rowId, value);

        return true;
    }

    /**
     * Adds fingerprint and smiles to the index for a sdf entry.
     *
     * @param rowId
     * @param sdf
     * @return
     */
    private boolean createEntryFromSdf(RowKey rowId, String sdf) {
        var molOpt = RDKitOps.sdfToMol(sdf);
        if (molOpt.isEmpty()) {
            logger.warn(fullSchemaName() + " : " + rowId.toString() + " bad sdf entry : "
                    + sdf);
            return false;
        }
        var mol = molOpt.get();
        String smiles = RDKitOps.canonicalize(mol);

        if (StringUtils.isEmpty(smiles)) {
            logger.warn(fullSchemaName() + " : " + rowId.toString() + " bad sdf entry : "
                    + sdf);
            return false;
        }

        boolean ok = createEntryCommon(smiles, rowId, mol);
        if (!ok) {
            logger.warn(fullSchemaName() + " : " + rowId.toString()
                    + " failed to fingerprint sdf entry: " + sdf + "[canonicalized to "
                    + smiles + "]");
        }

        return ok;
    }

    /**
     * Adds fingerprint and canonicalized smiles to the index for an molecule
     * entry.
     *
     * @param rowId
     * @param mol
     * @return
     */
    private boolean createEntryFromMol(RowKey rowId, ROMol mol) {
        var cansmi = RDKitOps.canonicalize(mol);
        boolean ok = createEntryCommon(cansmi, rowId, mol);
        if (!ok) {
            logger.warn(fullSchemaName() + " : " + rowId.toString()
                    + " failed to fingerprint molecule entry: " + "[canonicalized to "
                    + cansmi + "]");
        }
        return ok;
    }

    /**
     * Saves the index asynchronously
     */
    private void saveIndexAsynchronously() {
        Thread t = new Thread(() -> {
            try {
                saveIndexSynchronously();
            } catch (TaskException e) {
                logger.error("Task exception saving index asynchronously", e);
            } catch (RuntimeException e) {
                logger.error("Runtime exception saving index asynchronously", e);
            }
        });

        logger.info("Saving index asynchronously");
        t.start();
    }

    /**
     * Saves the index synchronously
     */
    private void saveIndexSynchronously() throws TaskException {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        if (LOCAL_INDEX) {
            saveLocalCache();
        } else {
            saveIndexToOracle();
        }
        stopWatch.stop();
        logger.info("Took " + stopWatch.getTime() / 1000.0 + " seconds to save lookups");
        // Util.printMemoryUsage(logger);
    }

    /**
     * Saves the index into Oracle.
     */
    private void saveIndexToOracle() throws TaskException {
        Connection connection = null;
        try {

            connection = getConnection();

            DatabaseObject databaseObject = new DatabaseObject(connection,
                    fullSchemaName() + "_fingerprintLookup");
            databaseObject.setValue(lookup);
            databaseObject.save();
            logger.info("Saved lookup for " + fullSchemaName() + " to Oracle");
            connection.commit();

        } catch (SQLException e) {
            String message = "SQL error saving index to oracle";
            logger.error(message, e);
            throw new TaskException(message);
        } finally {
            closeConnection(connection);
        }
    }

    /**
     * Returns the cache directory to store the lookup into
     *
     * @return
     */
    private Optional<File> getCacheDir() {
        if (indexInfo.cacheName != null) {
            var dir = new File(TaskManagerImpl.getInstallDir() + File.separatorChar + "cache",
                    indexInfo.cacheName);
            return Optional.of(dir);
        } else
            return Optional.empty();
    }

    /**
     * Save the lookup locally, with only index information in Oracle.
     */
    private synchronized void saveLocalCache() {
        if (indexInfo == null) {
            indexInfo = new TableIndexInfo();
        }
        if (indexInfo.cacheName == null) {
            indexInfo.cacheName = "structureCache" + CommonUtils.getUniqLabel();
            File cacheDir = getCacheDir().get();
            if (!cacheDir.mkdirs()) {
                String message = "Failed to create cache directory " + cacheDir;
                logger.error(message);
                throw new RuntimeException(message);
            }
        }

        File cacheDir = getCacheDir().get();
        // remove index file
        File indexFile = new File(cacheDir, INDEX_FILE);
        if (indexFile.exists()) {
            if (!indexFile.delete())
                logger.warn("failed to delete existing index file");
        }

        // save existing index file
        CommonUtils.objectToFile(indexFile, lookup);
        logger.info("Saved lookup to local file " + indexFile);

        // save index info
        Connection connection = null;
        try {
            connection = getConnection();
            DatabaseObject databaseObject = new DatabaseObject(connection,
                    fullSchemaName() + "_indexInfo");
            databaseObject.setValue(indexInfo);
            databaseObject.save();
            connection.commit();
            logger.info("Saved index info for " + fullSchemaName() + " to Oracle");
        } catch (SQLException | TaskException e) {
            String message = "Exception saving index info for " + fullSchemaName()
                    + " to Oracle";
            logger.error(message);
            throw new RuntimeException(message);
        } finally {
            closeConnection(connection);
        }
    }

    /**
     * Delete the local cache
     */
    private synchronized void removeLocalCache() {
        // if the index has not been loaded then indexInfo will be null and we
        // won't know the cache directory.
        if (indexInfo == null)
            loadLocalCache();
        getCacheDir().ifPresentOrElse(cacheDir -> {
            // remove cache directory
            if (!FileUtils.deleteQuietly(cacheDir)) {
                String message = "Failed to remove cache directory " + cacheDir;
                logger.error(message);
                throw new RuntimeException(message);
            }
            logger.info("Removed cache directory for " + fullSchemaName());
        }, () -> logger.warn("No cache directory found for index " + fullSchemaName()));

    }

    /**
     * Loads the index from a local file
     *
     * @return
     */
    private synchronized boolean loadLocalCache() {
        Connection connection = null;
        try {
            connection = getConnection();
            DatabaseObject databaseObject = new DatabaseObject(connection,
                    fullSchemaName() + "_indexInfo");
            indexInfo = (TableIndexInfo) databaseObject.getValue();
            if (indexInfo == null) {
                logger.warn("Index info is empty- new index?");
                return false;
            }
        } catch (TaskException e) {
            String message = "Exception saving index info for " + fullSchemaName()
                    + " to Oracle";
            logger.error(message);
            throw new RuntimeException(message);
        } finally {
            closeConnection(connection);
        }
        logger.info("Retrieved index info for " + fullSchemaName() + " from Oracle");

        File cacheDir = getCacheDir().get();
        File indexFile = new File(cacheDir, INDEX_FILE);
        @SuppressWarnings("unchecked")
        Map<RowKey, FingerprintedSmiles> localLookup = (Map<RowKey, FingerprintedSmiles>) CommonUtils
                .fileToObject(indexFile);
        lookup = localLookup;

        logger.info("Loaded lookup from index file " + indexFile);
        return true;
    }

    /*
     * (non-Javadoc)
     *
     * @see com.cairn.rmi.index.IndexBase#saveIndex()
     */
    @Override
    public synchronized void saveIndex() {
        saveIndexAsynchronously();
    }

    /**
     * Removes a stored Index and any change info from Oracle
     */
    @Override
    public synchronized void deleteIndex() throws TaskException {
        if (LOCAL_INDEX) {
            removeLocalCache();
        } else {
            removeIndexFromOracle();
        }
    }

    private void removeIndexFromOracle() {
        Connection connection = null;
        try {
            connection = getConnection();
            DatabaseObject databaseObject = new DatabaseObject(connection,
                    fullSchemaName() + "_fingerprintLookup");
            databaseObject.delete();
            connection.commit();
            logger.warn("Deleted java object " + fullSchemaName() + "_fingerprintLookup");
        } catch (SQLException e) {
            String message = "SQL error deleting index from oracle";
            logger.error(message, e);
            throw new RuntimeException(message);
        } finally {
            closeConnection(connection);
        }
    }

    @Override
    public void loadIndexOrBuild() throws TaskException {
        // If present retrieves the index from Oracle, otherwise builds and
        // saves.
        loadIndex();
        if (MapUtils.isEmpty(lookup)) {
            buildIndex();
            processLogTable();
            saveIndex();
        }
    }

    /**
     * Retrieves a serialized index from Oracle. Then adds in any entries saved
     * in log table.
     */
    @Override
    public synchronized void loadIndex() throws TaskException {

        if (isIndexLoaded()) {
            logger.debug("Index is already loaded");
            return;
        }

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        // Retrieves a serialized index from Oracle. Then adds in any entries
        // saved
        // in log table.

        if (LOCAL_INDEX) {
            loadLocalCache();
        } else {
            loadIndexFromOracle();
        }

        setIndexLoaded(true);
        processLogTable();

        stopWatch.stop();
        logger.info("Took " + stopWatch.getTime() / 1000.0
                + " seconds to retrieve lookup of size " + lookup.size());
        // Util.printMemoryUsage(logger);

    }

    /**
     * Loads the lookup from Oracle
     *
     * @throws TaskException
     */
    private void loadIndexFromOracle() throws TaskException {
        Connection connection = null;
        try {

            connection = getConnection();

            DatabaseObject databaseObject = new DatabaseObject(connection,
                    fullSchemaName() + "_fingerprintLookup");
            @SuppressWarnings("unchecked")
            Map<RowKey, FingerprintedSmiles> lookup = (Map<RowKey, FingerprintedSmiles>) databaseObject
                    .getValue();
            logger.info("Retrieved fingerprint lookup for " + fullSchemaName()
                    + " from Oracle");
            connection.commit();

            if (lookup == null) {
                logger.warn("lookup is empty");
                return;
            }

            logger.debug("Retrieved lookup contains " + lookup.size() + " entries");
            this.lookup = lookup;

        } catch (SQLException e) {
            String message = "SQL error loading index";
            logger.error(message, e);
            throw new TaskException(message);
        } finally {
            closeConnection(connection);
        }

    }

    /**
     * Synchronizes a single rowid from Oracle to the in-memory index. Only been
     * used for testing.
     *
     * @param rowKey
     */
    private synchronized void updateRow(RowKey rowKey) {
        String query = "select  " + columnName + " from " + ownerName + "." + tableName
                + " t where rowid = ?";

        logger.debug("Query is " + query);
        Connection connection = null;
        try {
            connection = SqlUtil.getOracleConnection(getConnection());
            OraclePreparedStatement preparedStatement = (OraclePreparedStatement) connection //
                    .prepareStatement(query);
            ROWID rowId = rowKey.getROWID();
            preparedStatement.setROWID(1, rowId);
            ResultSet resultSet = preparedStatement.executeQuery();

            if (resultSet.next()) {
                String smiles = resultSet.getString(1);
                String oldSmiles = lookup.get(rowKey).getSmiles();
                logger.debug("smiles is " + smiles + " old smiles is " + oldSmiles
                        + " cmp " + smiles.equals(oldSmiles));
                lookup.remove(rowKey);
                createEntryFromSmiles(new RowKey(rowId), smiles);
            } else {
                lookup.remove(rowKey);
            }
            preparedStatement.close();
        } catch (SQLException e) {
            String message = "SQLException updating row";
            logger.error(message, e);
            throw new RuntimeException(message);
        } finally {
            closeConnection(connection);
        }
    }

    private interface TargetMatcher {
        boolean matchTarget(RowKey rowKey);
    }

    private class SubstructureSearchCommon implements TargetMatcher {
        private final TaskJobResults taskJobResults;
        private final SubstructureSearchPool substructureSearchPool;
        private final SubstructureMatcher matcher;
        private final int maxHits;
        private final int jobNo;
        private int nMatches = 0;
        private int count = 0;

        private SubstructureSearchCommon(int jobNo, String query, String stringQueryType,
                                         int maxHits) {
            // setup the query
            var queryType = SubstructureMatcher.SubSearchQueryType.fromString(stringQueryType);
            matcher = new SubstructureMatcher(queryType, query);
            taskJobResults = new TaskJobResults(jobNo, false);
            substructureSearchPool = SubstructureSearchPool
                    .isUseSubstructureSearchPool() ? SubstructureSearchPool.getInstance()
                    : null;
            if (substructureSearchPool != null)
                substructureSearchPool.startSearch(taskJobResults, matcher, maxHits);
            this.maxHits = maxHits;
            this.jobNo = jobNo;
        }

        public boolean matchTarget(RowKey rowKey) {
            count++;
            if (count % 100000 == 0)
                logger.debug("Searched " + count + " compounds");

            var rowValue = lookup.get(rowKey);
            String target = rowValue.getSmiles();

            var targetFingerprint = rowValue.getFingerprint();


            if (substructureSearchPool != null) {
                if (!substructureSearchPool.submitMolSearch(jobNo, rowKey, target, true, targetFingerprint)) {
                    logger.debug("Got maxhits from subsearch pool");
                    return true;
                }
            } else {
                boolean match = matcher.matchStructure(target, true, targetFingerprint);

                if (match) {
                    taskJobResults.addHit(rowKey.getRowId(), null);
                    nMatches++;
                    if (maxHits > 0 && nMatches >= maxHits)
                        return true;
                }
            }

            return false;
        }

        private int finish() {
            if (MoleculeCache.isUseMoleculeCache())
                MoleculeCache.getMoleculeCache().info();
            if (substructureSearchPool != null) {
                nMatches = substructureSearchPool.finishSearch(jobNo);
            } else {
                taskJobResults.finish();
            }
            matcher.free();
            return nMatches;
        }
    }

    /**
     * Perform substructure search on the index.
     *
     * @param query   smarts pattern
     * @param maxHits
     * @return a list of rowids that match the query.
     * @throws Exception
     */
    public void substructureSearch(int jobNo, String query, String stringQueryType,
                                   int maxHits) throws TaskException {

        logger.info("Doing substructure search on " + fullSchemaName() + " : " + query
                + " query length " + query.length());
        logger.debug("Query Type is " + stringQueryType);

        // setup the query
        var search = new SubstructureSearchCommon(jobNo, query, stringQueryType, maxHits);
        // any new entries
        processLogTable();

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        for (RowKey rowKey : lookup.keySet()) {
            if (search.matchTarget(rowKey))
                break;
        }

        search.finish();


        double timeTaken = stopWatch.getTime() / 1000.0;
        stopWatch.stop();

        var matcher = search.matcher;
        logger.info("Substructure search object: count " + matcher.getnProcessed() + " screenout " + matcher.getScreenOut()
                + " hits " + matcher.getnHits());
        logger.info("Substructure search of " + search.count + " compounds, got " + search.nMatches + " hits in " + timeTaken
                + " seconds ");

    }

    private void searchSqlFilter(TargetMatcher search, String sqlFilter, String[] bindParams) throws TaskException {
        try (var connection = getConnection();
             PreparedStatement preparedStatement = SqlUtil.getOracleConnection(connection)
                     .prepareStatement(sqlFilter)) {
            if (bindParams != null) {
                for (int i = 0; i < bindParams.length; i++) {
                    preparedStatement.setString(i + 1, bindParams[i]);
                }
            }

            preparedStatement.execute();
            OracleResultSet resultSet = (OracleResultSet) preparedStatement
                    .getResultSet();

            while (resultSet.next()) {

                ROWID rowid = resultSet.getROWID(1);
                RowKey rowKey = new RowKey(rowid);
                if (search.matchTarget(rowKey)) {
                    break;
                }

            }

            resultSet.close();

        } catch (SQLException e) {
            String message = "SQL Exception in substructure search sql filter";
            logger.error(message, e);
            throw new RuntimeException(message);
        }

    }

    /**
     * Perform substructure search on the index, testing only molecules which
     * hit an SQL query.
     *
     * @param sqlFilter  SQl query which should return ROWIDS
     * @param query      smarts pattern
     * @param maxHits
     * @param bindParams Array of bound parameter values.
     * @return a list of rowids that match the query.
     * @throws Exception
     */
    public void substructureSearchSqlFilter(int jobNo, String sqlFilter, String query,
                                            String stringQueryType, int maxHits, String[] bindParams)
            throws TaskException {

        logger.info("Doing substructure search on " + query + " using sql filter "
                + sqlFilter);

        // setup the query
        var search = new SubstructureSearchCommon(jobNo, query, stringQueryType, maxHits);
        // any new entries
        processLogTable();

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        searchSqlFilter(search, sqlFilter, bindParams);

        search.finish();

        stopWatch.stop();
        double timeTaken = stopWatch.getTime() / 1000.0;
        logger.info("SQL query filter then Substructure search of " + search.count
                + " compounds, got " + search.nMatches + " hits in " + timeTaken + " seconds ");

    }

    /**
     * Performs exact match search against a structural smiles.
     *
     * @param smiles
     * @param maxHits
     * @return
     * @throws Exception
     */
    public void exactMatchSearch(int jobNo, String smiles, int maxHits)
            throws TaskException {

        // any new entries ?
        processLogTable();

        TaskJobResults taskJobResults = new TaskJobResults(jobNo, false);
        // canonicalize smiles
        var cansmiOpt = RDKitOps.canonicalize(smiles);
        if (cansmiOpt.isEmpty()) {
            logger.warn("exactmatchSearch bad smiles : " + smiles);
            return;
        }
        var cansmi = cansmiOpt.get();
        logger.info("Doing exact search on input " + smiles + " canonicalized to "
                + cansmi);

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        int count = 0;
        int nHits = 0;

        for (RowKey rowKey : lookup.keySet()) {
            String targetSmiles = lookup.get(rowKey).getSmiles();
            count++;

            if (targetSmiles == null)
                continue;
            if (cansmi.equals(targetSmiles)) {
                taskJobResults.addHit(rowKey.getRowId(), null);
                nHits++;
                if (maxHits > 0 && nHits >= maxHits)
                    break;
            }

            if (count % 100000 == 0)
                logger.debug("Searched " + count + " compounds");
        }

        double timeTaken = stopWatch.getTime() / 1000.0;
        stopWatch.stop();
        logger.info("Exact match search of " + count + " compounds, got " + nHits
                + " hits in " + timeTaken + " seconds ");

        taskJobResults.finish();
    }

    /**
     * Updates Oracle with the fingerprint and canonical smiles stored in the
     * index.
     * <p>
     * The query should return a rowid in column 1 and unique id in column 2.
     * E.g.
     * <p>
     * select rowid, molecule_id from molecules
     * <p>
     * The update sql should take 3 bind parameters: smiles, fingerprint and
     * unique id in that order. E.g.
     * <p>
     * insert into mol_info (smiles, fingerprint, id) values (:1, :2, :3)
     * <p>
     * or
     * <p>
     * update molecules set canonical_smiles = :1, string_fingerprint = :2 where
     * molecule_id = :3
     *
     * @param query
     * @param update
     * @param user
     * @param password
     * @throws SQLException
     */
    public void extractSmiles(String query, String update, String user, String password) {

        logger.info("Doing smiles extraction on " + fullSchemaName() + " query " + query
                + " update " + update);
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        try (var connection = SqlUtil.getConnection(TaskUtil.getHost(), TaskUtil.getDatabase(), String.valueOf(TaskUtil.getPort()), user, password);
             var queryStatement = SqlUtil.getOracleConnection(connection)
                     .prepareStatement(query);
             var updateStatement = connection.prepareStatement(update)) {

            queryStatement.execute();
            OracleResultSet resultSet = (OracleResultSet) queryStatement.getResultSet();

            int count = 0;
            while (resultSet.next()) {
                ROWID rowid = resultSet.getROWID(1);
                String id = resultSet.getString(2);
                FingerprintedSmiles fingerprintedSmiles = lookup.get(new RowKey(rowid));
                if (fingerprintedSmiles != null) {
                    updateStatement.setString(1, fingerprintedSmiles.getSmiles());
                    updateStatement.setString(2, fingerprintedSmiles.getStringFingerprint());
                    updateStatement.setString(3, id);
                    updateStatement.execute();
                    count++;
                    if (count % 10000 == 0)
                        logger.info("extracted " + count + " smiles");
                }
            }

            connection.commit();

            double timeTaken = stopWatch.getTime() / 1000.0;
            stopWatch.stop();
            logger.info("extracted " + count + " smiles in " + timeTaken + " seconds");
        } catch (SQLException e) {
            String message = "SQLException extracting smiles";
            logger.error(message, e);
            throw new RuntimeException(message);
        }
    }

    private class SimilaritySearchCommon implements TargetMatcher {
        private final int jobNo;
        private final double minSimilarity;
        private final ROMol query;
        private final int maxHits;
        private int nScreenout;
        private int nHits;
        private int count;
        private final BitSet queryFingerprint;
        private final double nQueryBits;
        private final TaskJobResults taskJobResults;
        private final int[] queryOnBits;

        private SimilaritySearchCommon(int jobNo, String smiles, double minSimilarity,
                                       int maxHits) {
            this.jobNo = jobNo;
            this.minSimilarity = minSimilarity;
            taskJobResults = new TaskJobResults(jobNo, true);

            this.maxHits = maxHits;
            var queryOpt = RDKitOps.smilesToMol(smiles, false);
            query = queryOpt.orElse(null);
            if (queryOpt.isEmpty()) {
                logger.warn("similaritySearch bad smiles : " + smiles);
                nQueryBits = 0;
                queryOnBits = null;
                queryFingerprint = null;
                return;
            }

            var fingerprint = RDKitOps.patternFingerPrintMol(query);
            if (fingerprint == null) {
                logger.warn("similaritySearch unable to fingerprint : " + smiles);
                nQueryBits = 0;
                queryOnBits = null;
                queryFingerprint = null;
                return;
            }
            queryFingerprint = RDKitOps.explictBitVectToBitSet(fingerprint);
            queryOnBits = IntStream.range(0, queryFingerprint.size()).filter(queryFingerprint::get).toArray();
            nQueryBits = (double) queryFingerprint.cardinality();
            logger.info("Doing similarity search on input " + smiles);
        }

        public boolean matchTarget(RowKey rowKey) {
            count++;

            var targetFingerprint = lookup.get(rowKey).getFingerprint();
            var nTargetBits = (double) targetFingerprint.cardinality();

            double maxSimilarity = nQueryBits > nTargetBits ? ((double) nTargetBits)
                    / ((double) nQueryBits) : ((double) nQueryBits)
                    / ((double) nTargetBits);
            if (maxSimilarity < minSimilarity)
                return false;

            var nCommon = 0;
            for (var on : queryOnBits) {
                if (targetFingerprint.get(on))
                    nCommon++;
            }
            var doubleCommon = (double) nCommon;
            var similarity = doubleCommon / (nTargetBits + nQueryBits - doubleCommon);
            nScreenout++;

            if (similarity >= minSimilarity) {
                nHits++;
                taskJobResults.addHit(rowKey.getRowId(), similarity);
                if (maxHits > 0 && nHits >= maxHits)
                    return true;
            }
            if (count % 100000 == 0)
                logger.debug("Searched " + count + " compounds");
            return false;
        }

        private void finish() {
            taskJobResults.finish();
        }
    }


    /**
     * Performs similarity search on the index
     *
     * @param jobNo
     * @param smiles
     * @param minSimilarity
     * @param maxHits
     * @throws TaskException
     */
    public void similaritySearch(int jobNo, String smiles, double minSimilarity,
                                 int maxHits) throws TaskException {

        var search = new SimilaritySearchCommon(jobNo, smiles, minSimilarity, maxHits);
        // any new entries
        processLogTable();

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        if (search.queryFingerprint == null)
            return;
        for (RowKey rowKey : lookup.keySet()) {
            if (search.matchTarget(rowKey))
                break;
        }
        search.finish();

        stopWatch.stop();
        double timeTaken = stopWatch.getTime() / 1000.0;
        logger.info("Similarity search of " + search.count + " compounds, got " + search.nHits
                + " hits, screenout of " + search.nScreenout + " in " + timeTaken + " seconds ");

    }

    /**
     * @param rowId1
     * @param rowId2
     * @return The pair-wise Tanimoto similarity between two rows.
     */
    public double rowSimilarity(ROWID rowId1, ROWID rowId2) {
        FingerprintedSmiles fingerprintedSmiles1 = getRowValue(rowId1);
        if (fingerprintedSmiles1 == null)
            return 0;
        FingerprintedSmiles fingerprintedSmiles2 = getRowValue(rowId2);
        if (fingerprintedSmiles2 == null)
            return 0;

        var fingerprint1 = fingerprintedSmiles1.getFingerprint();
        var fingerprint2 = fingerprintedSmiles2.getFingerprint();

        var fp1OnBits = IntStream.range(0, fingerprint1.size()).filter(fingerprint1::get).toArray();
        var n1 = (double) fingerprint1.cardinality();
        var n2 = (double) fingerprint2.cardinality();
        var nCommon = 0;
        for (var on : fp1OnBits) {
            if (fingerprint2.get(on))
                nCommon++;
        }
        var doubleCommon = (double) nCommon;
        return doubleCommon / (n1 + n2 - doubleCommon);
    }

    /**
     * @param rowId
     * @return The smiles stored for a row
     */
    public String getRowSmiles(ROWID rowId) {
        FingerprintedSmiles fingerprintedSmiles = getRowValue(rowId);
        if (fingerprintedSmiles == null)
            return null;
        return fingerprintedSmiles.getSmiles();
    }

    /**
     * @param rowId
     * @return A string representation of the fingerprint stored for a given
     * row.
     */
    public String getRowStringFingerprint(ROWID rowId) {
        FingerprintedSmiles fingerprintedSmiles = getRowValue(rowId);
        if (fingerprintedSmiles == null)
            return null;
        return fingerprintedSmiles.getStringFingerprint();
    }

    /**
     * Performs similarity search on the index
     *
     * @param jobNo
     * @param sqlFilter     SQl query which should return ROWIDS
     * @param query         a structural smiles
     * @param minSimilarity
     * @param maxHits
     * @param bindParams
     * @throws TaskException
     */
    public void similaritySearchSqlFilter(int jobNo, String sqlFilter, String query,
                                          double minSimilarity, int maxHits, String[] bindParams) throws TaskException {

        var search = new SimilaritySearchCommon(jobNo, query, minSimilarity, maxHits);

        // any new entries
        processLogTable();

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        if (search.queryFingerprint == null)
            return;
        searchSqlFilter(search, sqlFilter, bindParams);
        search.finish();

        stopWatch.stop();
        double timeTaken = stopWatch.getTime() / 1000.0;
        logger.info("SQL query filter then Similarity search of " + search.count
                + " compounds, got " + search.nHits + " hits, screenout of " + search.nScreenout
                + ", in " + timeTaken + " seconds ");
    }

    /**
     * Adds all the molecules in the index to the cache of molecules.
     */
    public void addToCache() throws Exception {
        // only add things to cache if caching is turned on for structure search
        if (!MoleculeCache.isUseMoleculeCache())
            return;

        MoleculeCache moleculeCache = MoleculeCache.getMoleculeCache();

        // any new entries ?
        processLogTable();

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        logger.debug("Adding structures to cache");
        int nSmiles = 0;
        for (FingerprintedSmiles fingerprintedSmiles : lookup.values()) {
            String smiles = fingerprintedSmiles.getSmiles();
            logger.trace("Adding smiles no " + nSmiles + " " + smiles);

            moleculeCache.useMolecule((mol) -> {
            }, smiles);
            nSmiles++;
            if (nSmiles % 10000 == 0) {
                logger.info("Added " + nSmiles + " molecules to cache ");
                // MoleculeCache.printMemoryUsage();
            }
        }

        moleculeCache.info();
        stopWatch.stop();

        logger.info("Added " + nSmiles + " molecules to cache in " + stopWatch.getTime()
                / 1000.0 + " seconds ");
    }

    /*
     * (non-Javadoc)
     *
     * @see com.cairn.rmi.index.IndexBase#indexType()
     */
    @Override
    public String indexType() {
        return "table_index";
    }

    /*
     * (non-Javadoc)
     *
     * @see com.cairn.rmi.index.IndexBase#addRowChangeEntry(int,
     * com.cairn.rmi.index.RowKey, java.lang.Object, java.lang.Object)
     */
    @Override
    protected void addRowChangeEntry(int rowChangeId, RowKey rowid, Object newValue,
                                     Object oldValue) {
        createEntry(rowid, newValue);

    }

    /*
     * (non-Javadoc)
     *
     * @see com.cairn.rmi.index.IndexBase#removeRowChangeEntry(int,
     * com.cairn.rmi.index.RowKey, java.lang.Object)
     */
    @Override
    protected void removeRowChangeEntry(int rowChangeId, RowKey rowid, Object oldValue) {
        lookup.remove(rowid);
    }

    /**
     * @param rowid
     * @return The row value for a given rowid.
     */
    private FingerprintedSmiles getRowValue(ROWID rowid) {
        RowKey key = new RowKey(rowid);
        return lookup.get(key);
    }
}
