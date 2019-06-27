package com.cairn.rmi.oracle;

import com.cairn.rmi.TaskException;
import com.cairn.rmi.client.TaskProxy;
import com.cairn.rmi.common.HitListChunk;
import oracle.jdbc.driver.OracleDriver;
import oracle.sql.ARRAY;
import oracle.sql.ArrayDescriptor;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;

import java.io.IOException;
import java.io.Reader;
import java.sql.Array;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

/* For oracle 12 the code and referenced classes in this class need to be Java 6 compatible
   For oracle 18 Java 7 is supported.

   As of Oracle 18C you need to use the Oracle specific sql classes (such as oracle.sql.ArrayDescriptor) even
   though these are marked as Deprecated.
 */

// TODO Refactor to remove duplicate code

/**
 * Wrappers for use in PL/SQL
 *
 * @author Gareth Jones
 */
class Wrappers {

    private static final Logger logger = Logger.getLogger(Wrappers.class);

    private static final long REAP_TIME_MINUTES = 30;

    static {
        defaultLogger();
        logger.setLevel(Level.DEBUG);
    }

    /**
     * Set up default log4j. Logs to the Console only.
     */
    private static void defaultLogger() {
        System.err.println("Setting up default console log4j logging");
        Logger rootLogger = Logger.getRootLogger();
        rootLogger.setLevel(Level.INFO);
        rootLogger.removeAllAppenders();
        PatternLayout layout = new PatternLayout("%-5p %c - %m  [%t] (%F:%L)%n");
        rootLogger.addAppender(new ConsoleAppender(layout, ConsoleAppender.SYSTEM_OUT));
    }


    /**
     * Similarity scores for searches are cached in a Java map. This is the best
     * way (so far) for accessing similarity scores for the ancillary functions.
     * <p>
     * Also we cache rowIds here for fast retrieval using the functional form.
     * If no score is present a null value is used.
     */
    private static final Map<String, Map<Integer, JobScores>> scoresMap = new HashMap<String, Map<Integer, JobScores>>();

    static class JobScores {
        private final long timeCreated = System.currentTimeMillis();
        private final Map<String, Double> scores = new HashMap<String, Double>();
    }

    /**
     * Remove any scores that have been present for REAP_TIME_MINUTES or more.
     */
    private static void cleanUpJobScores() {
        logger.debug("Cleaning up job scores");
        long cutoff = REAP_TIME_MINUTES * 60 * 1000;
        long currentTime = System.currentTimeMillis();
        for (Entry<String, Map<Integer, JobScores>> rmiScores : scoresMap.entrySet()) {
            Map<Integer, JobScores> rmiJobs = rmiScores.getValue();
            for (Entry<Integer, JobScores> entry : rmiJobs.entrySet()) {
                long time = currentTime - entry.getValue().timeCreated;
                if (time > cutoff) {
                    int jobNo = entry.getKey();
                    rmiJobs.remove(jobNo);
                    logger.debug("Scores map hostnaame " + rmiScores.getKey() + " entry "
                            + jobNo + " cleared after " + REAP_TIME_MINUTES);
                }
            }
        }
    }

    /**
     * @param smiles
     * @return Isotropic molecular weight for a smiles.
     */
    public static double molecularWeight(String rmiHostName, String smiles)
            throws TaskException {
        Object result = TaskProxy.submit(rmiHostName,
                "com.cairn.rmi.task.MolecularWeightTask", smiles);
        double mw = (Double) result;
        logger.debug("Smiles " + smiles + " mw " + mw);
        return mw;
    }

    /**
     * @param smiles
     * @return Isotropic molecular weight for a smiles.
     */
    public static double exactMass(String rmiHostName, String smiles)
            throws TaskException {
        Object result = TaskProxy.submit(rmiHostName,
                "com.cairn.rmi.task.ExactMassTask", smiles);
        double mw = (Double) result;
        logger.debug("Smiles " + smiles + " mass " + mw);
        return mw;
    }

    /**
     * @param smiles
     * @return Canonicalize a smiles.
     */
    public static String canonicalizeSmiles(String rmiHostName, String smiles)
            throws TaskException {
        Object result = TaskProxy.submit(rmiHostName,
                "com.cairn.rmi.task.CanonicalizeTask", smiles);
        String canSmiles = (String) result;
        logger.debug("Smiles " + smiles + " canonicalized to " + canSmiles);
        return canSmiles;
    }

    /**
     * @param inputMolecule
     * @param fromFormat
     * @param toFormat
     * @return Input structure translated to a different format.
     */
    public static String translate(String inputMolecule, String fromFormat,
                                   String toFormat) throws TaskException {
        HashMap<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("fromFormat", fromFormat);
        parameters.put("toFormat", toFormat);
        parameters.put("input", inputMolecule);
        Object result = TaskProxy.submit(null, "com.cairn.rmi.task.TranslateTask",
                parameters);
        String outputMolecule = (String) result;
        logger.debug("Translated to:\n" + outputMolecule);
        return outputMolecule;
    }

    /**
     * Generates a single fingerprint.
     *
     * @param query         set true if the input structure is a smarts pattern.
     * @param inputMolecule
     * @return
     */
    public static String getFingerprint(boolean query, String inputMolecule)
            throws TaskException {
        HashMap<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("query", query);
        parameters.put("input", inputMolecule);
        Object result = TaskProxy.submit(null, "com.cairn.rmi.task.FingerprintTask",
                parameters);
        String fingerprint = (String) result;
        logger.debug("fingerprint : " + fingerprint);
        return fingerprint;
    }

    /**
     * Given two smiles strings determines pair-wise Tanimoto similarity
     *
     * @param structure1
     * @param structure2
     * @return Tanimoto similarity
     * @throws TaskException
     */
    public static double tanimotoSimilarity(String structure1, String structure2)
            throws TaskException {
        HashMap<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("structure1", structure1);
        parameters.put("structure2", structure2);
        Object result = TaskProxy.submit(null, "com.cairn.rmi.task.FingerprintTask",
                parameters);
        double similarity = (Double) result;
        logger.debug("similarity : " + similarity);
        return similarity;
    }

    /**
     * Loads an array of smiles into the RMI structure cache.
     *
     * @param pInputSmiles
     * @throws SQLException
     * @throws TaskException
     */
    public static void addStructuresToCache(Array pInputSmiles)
            throws SQLException, TaskException {

        // get smiles from oracle and put in java list
        String[] inputSmiles = (String[]) pInputSmiles.getArray();

        logger.info("Submitting com.cairn.rmi.task.MoleculeCacheTask");
        // submit task
        TaskProxy.submit(null, "com.cairn.rmi.task.MoleculeCacheTask", inputSmiles);
    }

    // These two functions are to enable the client tests to turn the cache on and off.

    public static void enableCache() throws TaskException {
        TaskProxy.submit(null, "com.cairn.rmi.task.MoleculeCacheTask", new String[]{"cacheOn"});
    }

    public static void disableCache() throws TaskException {
        TaskProxy.submit(null, "com.cairn.rmi.task.MoleculeCacheTask", new String[]{"cacheOff"});
    }

    /**
     * Propagates any exception returned in a hitlist chunk
     *
     * @throws TaskException
     */
    private static void processChunkException(String rmiHostName, HitListChunk chunk)
            throws TaskException {
        Throwable throwable = chunk.getException();
        if (throwable == null)
            return;

        removeScoresMap(rmiHostName, chunk.getJobNo());
        if (throwable instanceof TaskException)
            throw (TaskException) throwable;
        else if (throwable instanceof Error)
            throw (Error) throwable;
        else if (throwable instanceof RuntimeException)
            throw (RuntimeException) throwable;
        else
            throw new IllegalStateException("Not uncaught exception " + throwable);
    }

    /**
     * Performs exact match search on an external RMI structure index
     *
     * @param ownerName
     * @param tableName
     * @param query
     * @param hits
     * @param maxHits
     * @throws SQLException
     * @throws TaskException
     */
    public static void tableIndexExactMatchSearch(String rmiHostname, boolean[] finished,
                                                  boolean addToMap, int[] jobNo, String ownerName, String tableName,
                                                  String columnName, String query, Array[] hits, int maxHits)
            throws SQLException, TaskException {
        logger.info("Submitting com.cairn.rmi.task.TableIndexTask, exact match search");

        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("operation", "exact_match");
        parameters.put("owner_name", ownerName);
        parameters.put("table_name", tableName);
        parameters.put("column_name", columnName);
        parameters.put("max_hits", maxHits);
        parameters.put("query", query);

        submitAndRetrieveChunk(rmiHostname, finished, addToMap, jobNo, hits, parameters, null);
    }

    /**
     * Performs substructure search on an external RMI structure index
     *
     * @param ownerName
     * @param tableName
     * @param query
     * @param hits
     * @param maxHits
     * @throws SQLException
     * @throws TaskException
     */
    public static void tableIndexSubstructureSearch(String rmiHostname,
                                                     boolean[] finished, boolean addToMap, int[] jobNo, String ownerName,
                                                     String tableName, String columnName, String query, Array[] hits,
                                                     int maxHits, boolean useFingerprint, String queryType) throws SQLException,
            TaskException {
        logger.info("Submitting com.cairn.rmi.task.TableIndexTask, substructure search");

        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("operation", "substructure");
        parameters.put("owner_name", ownerName);
        parameters.put("table_name", tableName);
        parameters.put("column_name", columnName);
        parameters.put("max_hits", maxHits);
        parameters.put("query", query);
        parameters.put("use_fingerprint", useFingerprint);
        parameters.put("query_type", queryType);

        submitAndRetrieveChunk(rmiHostname, finished, addToMap, jobNo, hits, parameters, null);
    }

    private static void submitAndRetrieveChunk(String rmiHostname,
                                               boolean[] finished, boolean addToMap, int[] jobNo, Array[] hits,
                                               Map<String, Object> parameters, Array[] similarities) throws SQLException, TaskException {

        Connection connection = new OracleDriver().defaultConnection();
        ArrayDescriptor arrayDescriptor = ArrayDescriptor.createDescriptor("ROWIDARRAY",
                connection);

        // submit task
        HitListChunk chunk = (HitListChunk) TaskProxy.submit(rmiHostname,
                "com.cairn.rmi.task.TableIndexTask", parameters);
        processChunkException(rmiHostname, chunk);
        String[] hitList = chunk.getHitlist();
        finished[0] = chunk.isFinished();
        jobNo[0] = chunk.getJobNo();

        if (logger.isDebugEnabled()) {
            int nHits = hitList == null ? 0 : hitList.length;
            logger.debug("Got " + nHits + " hits for job " + chunk.getJobNo());
        }

        hits[0] = new ARRAY(arrayDescriptor, connection, hitList);

        if (addToMap) {
            addToScoresMap(rmiHostname, chunk);
        }

        if (similarities != null) {
            double[] simArray = chunk.getScores();
            arrayDescriptor = ArrayDescriptor.createDescriptor("DOUBLEARRAY", connection);
            similarities[0] = new ARRAY(arrayDescriptor, connection, simArray);
        }
    }

    /**
     * Performs substructure search on an external RMI structure index
     *
     * @param ownerName
     * @param tableName
     * @param hits
     * @param maxHits
     * @throws SQLException
     * @throws TaskException
     */
    public static void tableIndexClobSubstructureSearch(String rmiHostname,
                                                        boolean[] finished, boolean addToMap, int[] jobNo, String ownerName,
                                                        String tableName, String columnName, Clob queryClob,
                                                        Array[] hits, int maxHits, boolean useFingerprint, String queryType)
            throws SQLException, TaskException, IOException {
        logger.info("Submitting com.cairn.rmi.task.TableIndexTask, clob substructure search");

        String query = clobToString(queryClob);

        logger.info("Query is " + query);

        // submit task
        tableIndexSubstructureSearch(rmiHostname, finished, addToMap, jobNo, ownerName,
                tableName, columnName, query, hits, maxHits, useFingerprint, queryType);

    }

    /**
     * Performs similarity search on an external RMI structure index
     *
     * @param ownerName
     * @param tableName
     * @param query
     * @param minSimilarity
     * @param hits
     * @param similarities
     * @param maxHits
     * @throws SQLException
     * @throws TaskException
     */
    public static void tableIndexSimilaritySearch(String rmiHostname, boolean[] finished,
                                                  boolean addToMap, int[] jobNo, String ownerName, String tableName,
                                                  String columnName, String query, double minSimilarity,
                                                  Array[] hits, Array[] similarities, int maxHits)
            throws SQLException, TaskException {
        logger.info("Submitting com.cairn.rmi.task.TableIndexTask, similarity search");

        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("operation", "similarity");
        parameters.put("owner_name", ownerName);
        parameters.put("table_name", tableName);
        parameters.put("column_name", columnName);
        parameters.put("max_hits", maxHits);
        parameters.put("query", query);
        parameters.put("min_similarity", minSimilarity);

        submitAndRetrieveChunk(rmiHostname, finished, addToMap, jobNo, hits, parameters, similarities);
    }

    /**
     * Performs similarity search using extended fingerprints on an external table index
     * the first chunk of results
     *
     * @param finished
     * @param addToMap
     * @param jobNo
     * @param ownerName
     * @param tableName
     * @param columnName
     * @param fingerprintType
     * @param searchMethod
     * @param query
     * @param minSimilarity
     * @param hits
     * @param similarities
     * @param maxHits
     * @param arg1
     * @param arg2
     * @throws SQLException
     * @throws TaskException
     */
    public static void extendedTableIndexSimilaritySearch(String rmiHostname, boolean[] finished,
                                                          boolean addToMap, int[] jobNo, String ownerName, String tableName,
                                                          String columnName, String fingerprintType, String searchMethod, String query,
                                                          double minSimilarity, Array[] hits,
                                                          Array[] similarities, int maxHits, Double arg1, Double arg2)
            throws SQLException, TaskException {
        logger.info("Submitting com.cairn.rmi.task.TableIndexTask, extended similarity search");

        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("operation", "extended_similarity");
        parameters.put("fingerprint_type", fingerprintType);
        parameters.put("search_method", searchMethod);
        parameters.put("owner_name", ownerName);
        parameters.put("table_name", tableName);
        parameters.put("column_name", columnName);
        parameters.put("max_hits", maxHits);
        parameters.put("query", query);
        parameters.put("min_similarity", minSimilarity);
        parameters.put("arg1", arg1);
        parameters.put("arg2", arg2);

        submitAndRetrieveChunk(rmiHostname, finished, addToMap, jobNo, hits, parameters, similarities);
    }

    /**
     * Adds a chunk to the lookup map for fast hit and score retrieval
     *
     * @param chunk
     */
    private static void addToScoresMap(String rmiHostname, HitListChunk chunk) {
        int jobNo = chunk.getJobNo();
        String[] hitList = chunk.getHitlist();
        if (hitList == null)
            return;
        double[] scoresList = chunk.getScores();
        if (!scoresMap.containsKey(rmiHostname)) {
            scoresMap.put(rmiHostname, new HashMap<Integer, Wrappers.JobScores>());
        }
        if (!scoresMap.get(rmiHostname).containsKey(jobNo)) {
            cleanUpJobScores();
            scoresMap.get(rmiHostname).put(jobNo, new JobScores());
        }
        Map<String, Double> scores = scoresMap.get(rmiHostname).get(jobNo).scores;
        logger.debug("Adding scores to map for rmi hostname " + rmiHostname + " job "
                + jobNo);
        for (int i = 0; i < hitList.length; i++) {
            Double score = scoresList != null ? scoresList[i] : null;
            logger.trace("Adding score " + score + " for row " + hitList[i]);
            scores.put(hitList[i], score);
        }
        logger.trace("Finished adding scores");
    }

    /**
     * Gets the next chunk of hits from the RMI server.
     *
     * @param jobId
     * @param finished
     * @param addToMap
     * @param hits
     * @param scores
     * @throws SQLException
     * @throws TaskException
     */
    public static void retrieveChunk(String rmiName, int jobId, boolean[] finished,
                                     boolean addToMap, Array[] hits,
                                     Array[] scores) throws SQLException, TaskException {
        logger.info("Submitting com.cairn.rmi.task.HitListChunkTask");
        // submit task
        Connection connection = new OracleDriver().defaultConnection();
        ArrayDescriptor arrayDescriptor = ArrayDescriptor.createDescriptor("ROWIDARRAY",
                connection);

        HitListChunk chunk = (HitListChunk) TaskProxy.submit(rmiName,
                "com.cairn.rmi.task.HitListChunkTask", jobId);
        processChunkException(rmiName, chunk);
        String[] hitList = chunk.getHitlist();
        double[] simArray = chunk.getScores();
        finished[0] = chunk.isFinished();

        if (logger.isDebugEnabled()) {
            int nHits = hitList == null ? 0 : hitList.length;
            logger.debug("Got " + nHits + " hits for job " + jobId);
        }

        hits[0] = new ARRAY(arrayDescriptor, connection, hitList);
        arrayDescriptor = ArrayDescriptor.createDescriptor("DOUBLEARRAY", connection);
        scores[0] = new ARRAY(arrayDescriptor, connection, simArray);

        if (addToMap) {
            addToScoresMap(rmiName, chunk);
        }
    }

    /**
     * Gets a similarity score for a given search and row id.
     *
     * @param jobId
     * @param rowId
     * @return
     */
    public static Double getSimilarityScore(String rmiHostname, int jobId, String rowId) {
        if (!scoresMap.containsKey(rmiHostname)) {
            logger.warn("No scores map for rmi hostname  " + rmiHostname);
            return null;
        }
        JobScores jobScores = scoresMap.get(rmiHostname).get(jobId);
        if (jobScores == null) {
            logger.warn("No scores map for rmi hostname  " + rmiHostname + " job "
                    + jobId);
            return null;
        }
        Double score = jobScores.scores.get(rowId);
        // can remove scores as we consume them, but this assumes that the
        // ancillary operator appears only once in the SQL query
        // jobScores.scores.remove(rowId);
        logger.trace("Similarity for row " + rowId + " is " + score);
        return score;
    }

    /**
     * Determines if a particular row is a hit, for a given search.
     *
     * @param jobId
     * @param rowId
     * @return
     */
    public static boolean isHit(String rmiHostname, int jobId, String rowId) {
        if (!scoresMap.containsKey(rmiHostname)) {
            logger.warn("No scores map for rmi hostname  " + rmiHostname);
            return false;
        }
        JobScores scores = scoresMap.get(rmiHostname).get(jobId);
        if (scores == null) {
            logger.warn("No scores map for job " + jobId);
            return false;
        }
        boolean isHit = scores.scores.containsKey(rowId);
        logger.trace("Row " + rowId + " is a hit: " + isHit);
        return isHit;
    }

    /**
     * Dispose of cached similarity scores for a given search.
     *
     * @param jobId
     */
    public static void removeScoresMap(String rmiHostname, int jobId) {
        logger.debug("Removing scores map for job " + jobId);
        if (scoresMap.containsKey(rmiHostname))
            scoresMap.get(rmiHostname).remove(jobId);
        else
            logger.warn("removeScoresMap: no map for hostname " + rmiHostname);
    }

    /**
     * Performs a table operation (that does not require parameters) on an index
     * class (e.g. structure index task class).
     *
     * @param indexClassName
     * @param ownerName
     * @param tableName
     * @param columnName
     * @param operation
     * @param params
     * @return
     * @throws TaskException
     */
    private static boolean indexOperation(String rmiHostname, String indexClassName,
                                          String ownerName, String tableName, String columnName, String operation,
                                          String params) throws TaskException {
        logger.info("Submitting " + indexClassName + ", index " + operation);

        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("operation", operation);
        parameters.put("owner_name", ownerName);
        parameters.put("table_name", tableName);
        parameters.put("column_name", columnName);
        if (StringUtils.isNotEmpty(params))
            parameters.put("params", params);

        Boolean ok = (Boolean) TaskProxy.submit(rmiHostname, indexClassName, parameters);
        return ok;
    }

    public static boolean tableIndexOperation(String rmiHostname, String ownerName,
                                              String tableName, String columnName, String operation) throws SQLException,
            TaskException {
        return tableIndexOperation(rmiHostname, ownerName, tableName, columnName, operation, null);
    }

    /**
     * Performs a table operation (that does not require parameters) on a
     * structure search index
     * <p>
     * Operation can be
     * <p>
     * build Builds (or does a full rebuild) on an external RMI structure index.
     * <p>
     * save Saves an RMI index, committing the change log.
     * <p>
     * truncate Empties an external RMI structure index.
     * <p>
     * drop Removes an external RMI structure index from memory. Deletes java
     * object in Oracle.
     * <p>
     * load loads an external RMI structure index into memory.
     * <p>
     * unload Unloads an external RMI structure index from memory.
     * <p>
     * add_to_cache adds molecules to cache
     *
     * @param ownerName
     * @param tableName
     * @param columnName
     * @param operation
     * @return
     * @throws SQLException
     * @throws TaskException
     */
    public static boolean tableIndexOperation(String rmiHostname, String ownerName,
                                               String tableName, String columnName, String operation, String params) throws SQLException,
            TaskException {
        return indexOperation(rmiHostname, "com.cairn.rmi.task.TableIndexTask",
                ownerName, tableName, columnName, operation, params);
    }

    /**
     * Performs substructure search on an external RMI structure index, testing
     * only those compounds that pass an SQL query
     *
     * @param sqlFilter
     * @param ownerName
     * @param tableName
     * @param columnName
     * @param query
     * @param hits
     * @param maxHits
     * @param bindParamArray
     * @throws SQLException
     * @throws TaskException
     */
    public static void tableIndexSubstructureSearchSqlFilter(String rmiHostname,
                                                             boolean[] finished, boolean addToMap, int[] jobNo, String ownerName,
                                                             String tableName, String columnName, String sqlFilter, String query,
                                                             Array[] hits, int maxHits, Array bindParamArray, String queryType)
            throws SQLException, TaskException {
        logger.info("Submitting com.cairn.rmi.task.TableIndexTask, substructure search");

        Map<String, Object> parameters = new HashMap<String, Object>();
        String[] bindParams = (String[]) bindParamArray.getArray();
        parameters.put("operation", "substructure_sql_filter");
        parameters.put("owner_name", ownerName);
        parameters.put("table_name", tableName);
        parameters.put("column_name", columnName);
        parameters.put("max_hits", maxHits);
        parameters.put("query", query);
        parameters.put("query_type", queryType);
        parameters.put("bind_params", bindParams);
        parameters.put("sql_filter", sqlFilter);

        submitAndRetrieveChunk(rmiHostname, finished, addToMap, jobNo, hits, parameters, null);

    }

    /**
     * Performs similarity search on an external RMI structure index, testing
     * only those compounds that pass an SQL query.
     *
     * @param sqlFilter
     * @param ownerName
     * @param tableName
     * @param columnName
     * @param query
     * @param minSimilarity
     * @param hits
     * @param similarities
     * @param maxHits
     * @param bindParamArray
     * @throws SQLException
     * @throws TaskException
     */
    public static void tableIndexSimilaritySearchSqlFilter(String rmiHostname,
                                                           boolean[] finished, boolean addToMap, int[] jobNo, String ownerName,
                                                           String tableName, String columnName, String sqlFilter, String query,
                                                           double minSimilarity, Array[] hits,
                                                           Array[] similarities, int maxHits, Array bindParamArray)
            throws SQLException, TaskException {
        logger.info("Submitting com.cairn.rmi.task.TableIndexTask, similarity search");

        Map<String, Object> parameters = new HashMap<String, Object>();
        String[] bindParams = (String[]) bindParamArray.getArray();
        parameters.put("operation", "similarity_sql_filter");
        parameters.put("owner_name", ownerName);
        parameters.put("table_name", tableName);
        parameters.put("column_name", columnName);
        parameters.put("max_hits", maxHits);
        parameters.put("query", query);
        parameters.put("bind_params", bindParams);
        parameters.put("sql_filter", sqlFilter);
        parameters.put("min_similarity", minSimilarity);

        submitAndRetrieveChunk(rmiHostname, finished, addToMap, jobNo, hits, parameters, similarities);

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
     * @param ownerName
     * @param tableName
     * @param columnName
     * @param query
     * @param update
     * @param user
     * @throws TaskException
     */
    public static void tableIndexExtractSmiles(String rmiHostname, String ownerName,
                                               String tableName, String columnName, String query, String update,
                                               String user, String passwd) throws TaskException {
        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("operation", "extract_smiles");
        parameters.put("owner_name", ownerName);
        parameters.put("table_name", tableName);
        parameters.put("column_name", columnName);
        parameters.put("query", query);
        parameters.put("update", update);
        parameters.put("user", user);
        parameters.put("password", passwd);
        TaskProxy.submit(rmiHostname, "com.cairn.rmi.task.TableIndexTask",
                parameters);
    }

    /**
     * Gets the smiles that is stored in the RMI index for a given rowId
     *
     * @param ownerName
     * @param tableName
     * @param columnName
     * @param rowid
     * @return
     * @throws TaskException
     */
    public static String tableIndexGetRowSmiles(String rmiHostname, String ownerName,
                                                String tableName, String columnName, String rowid) throws
            TaskException {

        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("operation", "get_row_smiles");
        parameters.put("owner_name", ownerName);
        parameters.put("table_name", tableName);
        parameters.put("column_name", columnName);
        parameters.put("row_id", rowid);

        return (String) TaskProxy.submit(rmiHostname,
                "com.cairn.rmi.task.TableIndexTask", parameters);

    }

    /**
     * Gets the fingerprint that is stored in the RMI index for a given rowId
     *
     * @param ownerName
     * @param tableName
     * @param columnName
     * @param rowid
     * @return
     * @throws TaskException
     */
    public static String tableIndexGetRowFingerprint(String rmiHostname,
                                                     String ownerName, String tableName, String columnName, String rowid)
            throws TaskException {

        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("operation", "get_row_fingerprint");
        parameters.put("owner_name", ownerName);
        parameters.put("table_name", tableName);
        parameters.put("column_name", columnName);
        parameters.put("row_id", rowid);

        return (String) TaskProxy.submit(rmiHostname,
                "com.cairn.rmi.task.TableIndexTask", parameters);

    }

    /**
     * Determines the pair-wise Tanimoto similarity between two rows.
     *
     * @param ownerName
     * @param tableName
     * @param columnName
     * @param rowid1
     * @param rowid2
     * @return
     * @throws TaskException
     */
    public static double tableIndexGetRowSimilarity(String rmiHostname, String ownerName,
                                                    String tableName, String columnName, String rowid1, String rowid2)
            throws TaskException {

        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("operation", "tanimoto_smilarity");
        parameters.put("owner_name", ownerName);
        parameters.put("table_name", tableName);
        parameters.put("column_name", columnName);
        parameters.put("row_id1", rowid1);
        parameters.put("row_id2", rowid2);

        return (Double) TaskProxy.submit(rmiHostname,
                "com.cairn.rmi.task.TableIndexTask", parameters);

    }

    /**
     * Performs functional substructure search using an RMI service
     *
     * @param target
     * @param query
     * @param queryType
     * @return true if the target matches the query
     * @throws TaskException
     */
    private static boolean functionalSubstructureSearch(String target, String query,
                                                        String queryType) throws TaskException {
        logger.info("Submitting com.cairn.rmi.task.FunctionalOperatorTask, substructure search");

        // submit task

        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("operation", "substructure");
        parameters.put("query", query);
        parameters.put("target", target);
        parameters.put("query_type", queryType);

        Boolean match = (Boolean) TaskProxy.submit(null,
                "com.cairn.rmi.task.FunctionalOperatorTask", parameters);

        logger.trace("query " + query + " substructure "
                + (match ? " matches " : "  does not match") + target);
        return match;

    }

    /**
     * Performs functional substructure search using an RMI service
     *
     * @param target
     * @param queryClob
     * @param queryType
     * @return
     * @throws SQLException
     * @throws TaskException
     * @throws IOException
     */
    public static boolean functionalClobSubstructureSearch(String target,
                                                           java.sql.Clob queryClob, String queryType) throws SQLException,
            TaskException, IOException {
        logger.info("Submitting com.cairn.rmi.task.TableIndexTask, clob substructure search");
        String query = clobToString(queryClob);

        logger.info("Query is " + query);

        // submit task
        return functionalSubstructureSearch(target, query, queryType);
    }

    private static String clobToString(Clob clob) throws SQLException, IOException {
        // convert clob to string
        Reader clobReader = clob.getCharacterStream();
        char[] buffer = new char[4096];
        int nChars;
        StringBuilder sb = new StringBuilder();
        while ((nChars = clobReader.read(buffer)) != -1) {
            sb.append(buffer, 0, nChars);
        }
        clobReader.close();
        return sb.toString();
    }

    /**
     * Performs functional exact match search using an RMI service
     *
     * @param target
     * @param query
     * @return
     * @throws TaskException
     */
    public static boolean functionalExactMatchSearch(String target, String query)
            throws TaskException {
        logger.info("Submitting com.cairn.rmi.task.FunctionalOperatorTask, exact match search");

        // submit task

        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("operation", "exact_match");
        parameters.put("query", query);
        parameters.put("target", target);

        Boolean match = (Boolean) TaskProxy.submit(null,
                "com.cairn.rmi.task.FunctionalOperatorTask", parameters);

        logger.debug("query " + query
                + (match ? " exactly matches " : " does not exactly match ") + target);
        return match;

    }

    /**
     * Performs functional similarity search using an RMI service
     *
     * @param target
     * @param query
     * @return
     * @throws TaskException
     */
    public static boolean functionalSimilaritySearch(String target, String query,
                                                     double minSimilarity) throws TaskException {
        logger.info("Submitting com.cairn.rmi.task.FunctionalOperatorTask, similarity search");

        // submit task

        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("operation", "similarity");
        parameters.put("query", query);
        parameters.put("target", target);
        parameters.put("min_similarity", minSimilarity);

        Boolean match = (Boolean) TaskProxy.submit(null,
                "com.cairn.rmi.task.FunctionalOperatorTask", parameters);

        logger.debug("query " + query + (match ? "  matches " : "does not  match")
                + target + " with a similarity of " + minSimilarity);

        return match;

    }
}
