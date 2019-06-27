package com.cairn.rmi.task;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import oracle.sql.ROWID;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import com.cairn.rmi.TaskException;
import com.cairn.rmi.index.TableIndex;
import com.cairn.rmi.server.TaskJob;
import com.cairn.rmi.server.TaskUtil;

/**
 * Task to drive table substructure index operations from RMI. Thread safe,
 * though it is assumed that the index operations are applied from the Oracle
 * data cartridge- it other words we can guarantee that, for example, an index
 * build and truncate will not happen simultaneously.
 * <p>
 * Settings is a map containing table_name and owner_name keys to defined the
 * database schema table together with an operation key.
 * <p>
 * Operations values include
 * <p>
 * substructure
 * <p>
 * Does substructure search: requires query key (containing smarts as a value)
 * and max_hits key. Returns an array of matching rowids (as string values).
 * Also returned is the batch job id for retrieving additional hits together
 * with a finished key to indicate if there are more results.
 * <p>
 * similarity
 * <p>
 * Does similarity search: requires query key (containing smiles as a value) and
 * keys for max_hits and min_similarity. Returns a map of two keys: hits (with
 * an array value of matching rowids as string values) similarities (with an
 * array value of similarity scores). Also returned is the batch job id for
 * retrieving additional hits together with a finished key to indicate if there
 * are more results.
 * <p>
 * exact_match
 * <p>
 * Does exact match search: requires query key (containing smiles as a value)
 * and max_hits key. Returns an array of matching rowids (as string values).
 * Also returned is the batch job id for retrieving additional hits together
 * with a finished key to indicate if there are more results.
 * <p>
 * build
 * <p>
 * Does a full build (or rebuild) of the index.
 * <p>
 * drop
 * <p>
 * Removes an index from memory and the stored Java Object from Oracle. Does not
 * remove the change log .
 * <p>
 * truncate
 * <p>
 * Removes an index from memory and replaces it with an empty index, which is
 * save back to Oracle. Does not truncate the change log table.
 * <p>
 * <p>
 * save
 * <p>
 * Saves the index back to Oracle. This commits all changes in the change log
 * file.
 * <p>
 * unload
 * <p>
 * Removes an index from memory. Need this for table/column rename.
 * <p>
 * load
 * <p>
 * Loads an index into memory. Note this will build the index if it doesn't
 * exist.
 * <p>
 * extract_smiles
 * <p>
 * Writes back Canonical smiles from the domain index to the database. Requires
 * rigor in the passed SQL. Requires query and update keys containing sql and
 * user and password keys for update credentials. The query sql should return
 * unique id and rowid. The update key uses the unique id to insert or update
 * canonical smiles and fingerprint data.
 * <p>
 * get_row_smiles
 * <p>
 * Gets the smiles associated with a row.
 * <p>
 * get_row_fingerprint
 * <p>
 * Gets the fingerprint associated with a row.
 * <p>
 * tanimoto_similarity
 * <p>
 * Returns the tanimoto similarity between two rowIds.
 * <p>
 * similarity_sql_filter
 * <p>
 * Like similarity with a pre-search sql filter command.
 * <p>
 * substructure_sql_filter
 * <p>
 * Like substructure with a pre-search sql filter command.
 *
 * @author Gareth Jones
 * @see TableIndex
 */
public class TableIndexTask extends AbstractTask {

    /**
     *
     */
    private static final long serialVersionUID = 1000L;
    private static final Logger logger = Logger.getLogger(TableIndexTask.class);

    // A map to store all current indexes by schema name
    private static final Map<String, TableIndex> indexes = new HashMap<>();

    private final static ConcurrentHashMap<String, Object> indexLock = new ConcurrentHashMap<>();

    /**
     * Create an object to lock an index key on.
     *
     * @param key
     * @return
     */
    private static Object getIndexLock(String key) {
        indexLock.putIfAbsent(key, new Object());
        return indexLock.get(key);
    }

    /**
     * Gets a table index. Return it if is already loaded or otherwise build or
     * retrieve.
     *
     * @param ownerName
     * @param tableName
     * @return
     * @throws Exception
     */
    public static TableIndex getTableIndex(String ownerName, String tableName,
                                           String columnName) throws Exception {
        String key = TaskUtil.getIndexKey(ownerName, tableName, columnName);

        synchronized (getIndexLock(key)) {
            if (indexes.containsKey(key))
                return indexes.get(key);

            TableIndex index = new TableIndex(ownerName, tableName, columnName);
            indexes.put(key, index);
            return index;
        }
    }

    @Override
    public Object submitTask() throws TaskException {

        Map<?, ?> parameters = (Map<?, ?>) settings;
        String tableName = (String) parameters.get("table_name");
        String ownerName = (String) parameters.get("owner_name");
        String columnName = (String) parameters.get("column_name");
        String infoName = ownerName + "." + tableName + "." + columnName;
        results = false;

        try {
            String operation = (String) parameters.get("operation");

            switch (operation) {
                case "substructure":
                case "substructure_sql_filter": {

                    final TableIndex index = getTableIndex(ownerName, tableName, columnName);
                    index.loadIndex();
                    logger.info("Substructure search on index " + infoName);
                    final String query = (String) parameters.get("query");
                    final String queryType = (String) parameters.get("query_type");
                    final int maxHits = (Integer) parameters.get("max_hits");
                    if (operation.equals("substructure")) {
                        // substructure search operation

                        TaskJob taskJob = new TaskJob() {
                            @Override
                            public void runSearch() throws TaskException {
                                index.substructureSearch(getJobNo(), query, queryType, maxHits);

                            }
                        };
                        results = taskJob.runJob();
                    } else {
                        // substructure search operation, with initial query filtering
                        final String sqlFilter = (String) parameters.get("sql_filter");
                        final String[] bindParams = (String[]) parameters.get("bind_params");

                        TaskJob taskJob = new TaskJob() {
                            @Override
                            public void runSearch() throws TaskException {
                                index.substructureSearchSqlFilter(getJobNo(),
                                        sqlFilter, query, queryType, maxHits, bindParams);
                            }
                        };
                        results = taskJob.runJob();
                    }

                    break;
                }
                case "similarity": {

                    // similarity search operation
                    final TableIndex index = getTableIndex(ownerName, tableName, columnName);
                    index.loadIndex();
                    logger.info("Similarity search on index " + infoName);
                    final String query = (String) parameters.get("query");
                    final int maxHits = (Integer) parameters.get("max_hits");
                    final double minSimilarity = (Double) parameters.get("min_similarity");
                    TaskJob taskJob = new TaskJob() {
                        @Override
                        public void runSearch() throws TaskException {
                            index.similaritySearch(getJobNo(), query,
                                    minSimilarity, maxHits);
                        }
                    };
                    results = taskJob.runJob();

                    break;
                }
                case "similarity_sql_filter": {

                    // similarity search operation, with initial query filtering
                    final TableIndex index = getTableIndex(ownerName, tableName, columnName);
                    index.loadIndex();
                    logger.info("Similarity search on index " + infoName);
                    final String query = (String) parameters.get("query");
                    final int maxHits = (Integer) parameters.get("max_hits");
                    final String sqlFilter = (String) parameters.get("sql_filter");
                    final String[] bindParams = (String[]) parameters.get("bind_params");
                    final double minSimilarity = (Double) parameters.get("min_similarity");
                    TaskJob taskJob = new TaskJob() {
                        @Override
                        public void runSearch() throws TaskException {
                            index.similaritySearchSqlFilter(getJobNo(), sqlFilter,
                                    query, minSimilarity, maxHits, bindParams);
                        }
                    };
                    results = taskJob.runJob();

                    break;
                }
                case "exact_match": {

                    // exact match search operation
                    final TableIndex index = getTableIndex(ownerName, tableName, columnName);
                    index.loadIndex();
                    logger.info("Exact match on index " + infoName);
                    final String query = (String) parameters.get("query");
                    final int maxHits = (Integer) parameters.get("max_hits");
                    TaskJob taskJob = new TaskJob() {
                        @Override
                        public void runSearch() throws TaskException {
                            index.exactMatchSearch(getJobNo(), query, maxHits);

                        }
                    };
                    results = taskJob.runJob();

                    break;
                }
                case "save": {

                    // save state of current index.
                    logger.info("Saving and committing index for " + infoName);
                    TableIndex index = getTableIndex(ownerName, tableName, columnName);
                    index.loadIndex();
                    // this command also checks the log table so the saved index
                    // should now include all the logs. Also empties log table.
                    index.commit();
                    results = true;

                    break;
                }
                case "build": {

                    // build an index
                    TableIndex index = getTableIndex(ownerName, tableName, columnName);
                    logger.info("Index build or rebuild full on " + infoName);
                    String params = (String) parameters.get("params");
                    index.createFingerprintTypes(params);
                    index.buildIndex();
                    results = true;

                    break;
                }
                case "drop": {

                    // remove an index from memory
                    // If you drop an index while performing some operations on it
                    // things will almost certainly crash. Hopefully, Oracle will
                    // prevent this.
                    logger.info("Dropping index from memory and Oracle " + infoName);
                    String key = TaskUtil.getIndexKey(ownerName, tableName, columnName);
                    synchronized (getIndexLock(key)) {
                        TableIndex index = getTableIndex(ownerName, tableName, columnName);
                        // remove the index from the lookup first in case an
                        // exception is thrown deleting the index
                        indexes.remove(key);
                        index.deleteIndex();
                        results = true;
                    }

                    break;
                }
                case "truncate": {

                    // create an empty index and save to Oracle
                    logger.info("Truncating index from memory and Oracle for " + infoName);
                    TableIndex index = getTableIndex(ownerName, tableName, columnName);
                    index.truncate();
                    results = true;

                    break;
                }
                case "unload": {

                    // removes index from memory
                    logger.info("Removing index from memory for " + infoName);
                    String key = TaskUtil.getIndexKey(ownerName, tableName, columnName);
                    synchronized (getIndexLock(key)) {
                        indexes.remove(key);
                        results = true;
                    }

                    break;
                }
                case "load": {

                    // adds index to memory
                    logger.info("Adding index to memory for " + infoName);
                    TableIndex index = getTableIndex(ownerName, tableName, columnName);
                    index.loadIndexOrBuild();
                    results = true;

                    break;
                }
                case "add_to_cache": {

                    // adds molecules in index to cache
                    logger.info("Adding molecules in index to cache for " + infoName);
                    TableIndex index = getTableIndex(ownerName, tableName, columnName);
                    index.loadIndex();
                    index.addToCache();
                    results = true;

                    break;
                }
                case "extract_smiles": {

                    // extracts canonical smiles, smiles status and fingerprints
                    // from the index
                    TableIndex index = getTableIndex(ownerName, tableName, columnName);
                    index.loadIndex();
                    logger.info("Extracting smiles and fingerprints from index " + infoName);
                    String query = (String) parameters.get("query");
                    String update = (String) parameters.get("update");
                    String user = (String) parameters.get("user");
                    String password = (String) parameters.get("password");
                    index.extractSmiles(query, update, user, password);
                    results = true;

                    break;
                }
                case "get_row_smiles":
                case "get_row_fingerprint": {

                    // extract smiles or fingerprint for a particular row
                    String rowIdString = (String) parameters.get("row_id");
                    ROWID rowId = new ROWID(rowIdString.getBytes());
                    TableIndex index = getTableIndex(ownerName, tableName, columnName);
                    index.loadIndex();
                    if (operation.equals("get_row_smiles")) {
                        results = index.getRowSmiles(rowId);
                    } else {
                        results = index.getRowStringFingerprint(rowId);
                    }

                    break;
                }
                case "tanimoto_smilarity": {

                    // determine tanimoto similarity between two rows
                    TableIndex index = getTableIndex(ownerName, tableName, columnName);
                    index.loadIndex();
                    String rowId1String = (String) parameters.get("row_id1");
                    ROWID rowId1 = new ROWID(rowId1String.getBytes());
                    String rowId2String = (String) parameters.get("row_id2");
                    ROWID rowId2 = new ROWID(rowId2String.getBytes());

                    results = index.rowSimilarity(rowId1, rowId2);

                    break;
                }
                case "extended_similarity": {

                    // similarity search operation
                    TableIndex index = getTableIndex(ownerName, tableName, columnName);
                    index.loadIndex();
                    logger.info("Similarity search on index " + infoName);
                    final String query = (String) parameters.get("query");
                    final int maxHits = (Integer) parameters.get("max_hits");
                    final double minSimilarity = (Double) parameters.get("min_similarity");
                    final String fingerprintType = (String) parameters.get("fingerprint_type");
                    final String searchMethod = (String) parameters.get("search_method");
                    final Double alpha = (Double) parameters.get("arg1");
                    final Double beta = (Double) parameters.get("arg2");
                    TaskJob taskJob = new TaskJob() {
                        @Override
                        public void runSearch() throws TaskException {
                            index.extendedSimilaritySearch(getJobNo(), fingerprintType,
                                    searchMethod, query, minSimilarity, maxHits,
                                    alpha, beta);
                        }
                    };
                    results = taskJob.runJob();

                    break;
                }
                default:
                    throw new TaskException("unknown operation " + operation);
            }

        } catch (Exception e) {
            logger.error("Exception", e);
            throw new TaskException("Exception" + e);
        }

        return results;

    }
}
