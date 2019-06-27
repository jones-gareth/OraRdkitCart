package com.cairn.rmi.index;

import com.cairn.common.SqlUtil;
import com.cairn.rmi.TaskException;
import com.cairn.rmi.server.TaskUtil;
import oracle.jdbc.internal.OracleResultSet;
import oracle.sql.ROWID;
import org.apache.log4j.Logger;

import java.sql.*;

/**
 * Base class for building indexes on smiles columns
 * <p>
 * Thread safe
 *
 * @author gjones
 */
public abstract class IndexBase {
    // Base table, owner and column for this index
    final String tableName;
    final String ownerName;
    final String columnName;
    private static final Logger logger = Logger.getLogger(IndexBase.class);
    private volatile boolean indexLoaded = false;

    // Change Log Table Name
    private volatile String logTable;
    // This is the last changed row we processed.
    private volatile int currentRowChangeId;

    public  enum IndexColumnType {
        SMILES, SDF, BINARY
    }

    final IndexColumnType indexColumnType;

    IndexBase(String ownerName, String tableName, String columnName) {
        super();
        this.ownerName = ownerName.toUpperCase();
        this.tableName = tableName.toUpperCase();
        this.columnName = columnName.toUpperCase();

        try {
            // identify index column type
            Connection connection = TaskUtil.getConnection();
            String query = "    select data_type from all_tab_columns "
                    + "     where owner = ? and table_name = ? and column_name = ?";
            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setString(1, this.ownerName);
            stmt.setString(2, this.tableName);
            stmt.setString(3, this.columnName);
            ResultSet resultSet = stmt.executeQuery();
            if (!resultSet.next()) {
                throw new IllegalStateException("No index entry for index "
                        + fullSchemaName());
            }
            String columnTypeString = resultSet.getString(1);
            resultSet.close();
            stmt.close();
            connection.close();
            switch (columnTypeString) {
                case "VARCHAR2":
                    indexColumnType = IndexColumnType.SMILES;
                    break;
                case "CLOB":
                    indexColumnType = IndexColumnType.SDF;
                    break;
                case "BLOB":
                    indexColumnType = IndexColumnType.BINARY;
                    break;
                default:
                    throw new IllegalStateException(
                            "Unable to assign index column type for Oracle column of type "
                                    + columnTypeString);
            }
        } catch (SQLException e) {
            String message = "SQL error trying to find index column type";
            logger.error(message, e);
            throw new RuntimeException(message, e);
        }

    }

    /**
     * @return The schema name for the table.
     */
    String fullSchemaName() {
        return ownerName + "." + tableName + "." + columnName;
    }

    /**
     * Gets a database connection
     *
     * @return
     */
    Connection getConnection() {
        return TaskUtil.getConnection();
    }

    /**
     * Closes/reuses database connection.
     *
     * @param connection
     * @throws SQLException
     */
    void closeConnection(Connection connection) {
        TaskUtil.closeConnection(connection);
    }

    /**
     * Specifies the index type.
     *
     * @return
     */
    protected abstract String indexType();

    /**
     * Checks to see if we have a change log table for this index.
     *
     * @throws SQLException
     */
    void identifyLogTable() throws TaskException {
        var query = "select change_table_name "
                + "from index_lookup where index_key = :1 and index_type =:2";
        try (var connection = getConnection();
             var preparedStatement = connection.prepareStatement(query)) {
            preparedStatement.setString(1, fullSchemaName());
            preparedStatement.setString(2, indexType());
            preparedStatement.execute();
            ResultSet resultSet = preparedStatement.getResultSet();
            if (resultSet.next()) {
                logTable = resultSet.getString(1);
                logger.info("Index " + fullSchemaName() + " has log table " + logTable);
            } else {
                logger.info("Index " + fullSchemaName() + " has no log table");
            }
            resultSet.close();
        } catch (SQLException e) {
            String message = "SQLException identifying log table";
            logger.error(message, e);
            throw new TaskException(message);
        }
    }

    /**
     * Build an index on a table that contains a string column containing
     * smiles. This operation should save the index and mark it a as loaded.
     *
     * @throws TaskException
     */
    public abstract void buildIndex() throws TaskException;

    /**
     * Saves the index to persistent storage
     *
     */
    protected abstract void saveIndex();

    /**
     * Completely removes the index from persistent storage.
     */
    public abstract void deleteIndex() throws TaskException;

    /**
     * Loads an index from persistent storage and mark it as loaded. If the
     * index is already loaded this should do nothing.
     *
     * @throws TaskException
     */
    public abstract void loadIndex() throws TaskException;

    /**
     * Loads an index if it exists or build it from scratch if it doesn't
     *
     * @throws TaskException
     */
    public abstract void loadIndexOrBuild() throws TaskException;

    /**
     * Updates the index with new entries in the log table.
     *
     * @return
     * @throws Exception
     */
    int processLogTable() throws TaskException {
        return processLogTable(false);
    }

    /**
     * Applies any new entries in the log table then saves the index and empties
     * the log table.
     *
     * @throws Exception
     */
    public void commit() throws TaskException {
        processLogTable(true);
    }

    /**
     * Truncates and saves the index.
     *
     * @throws TaskException
     */
    public abstract void truncate() throws TaskException;

    /**
     * Processes the log table adding and removing structures from the index as
     * appropriate. Setting commit will apply changes, empty the log table in
     * Oracle and saves the index.
     *
     * @param commit
     * @return number of entries processed
     * @throws SQLException
     */
    private synchronized int processLogTable(boolean commit) throws TaskException {
        if (logTable == null) {
            identifyLogTable();
            if (logTable == null)
                throw new TaskException("processLogTable: no change log table!");
        }
        int no = 0;
        logger.debug("Checking log table");
        String query = "select ROW_CHANGE_ID, ROW_CHANGED, NEW_VALUE, OLD_VALUE from "
                + logTable + " where row_change_id > :1 order by row_change_id";

        if (commit) {
            query += " for update";
            logger.debug("Commiting log table for " + fullSchemaName());
        }

        try (var connection = getConnection();
             var preparedStatement = SqlUtil.getOracleConnection(connection)
                     .prepareStatement(query)) {

            preparedStatement.setInt(1, currentRowChangeId);
            preparedStatement.execute();
            OracleResultSet resultSet = (OracleResultSet) preparedStatement
                    .getResultSet();
            int rowChangeId = 0;

            while (resultSet.next()) {
                no++;
                rowChangeId = resultSet.getInt(1);
                logger.debug("Processing row change id " + rowChangeId);
                ROWID rowId = resultSet.getROWID(2);
                Object newValue = retrieveRowValueFromDatabase(3, resultSet);
                Object oldValue = retrieveRowValueFromDatabase(4, resultSet);
                RowKey key = new RowKey(rowId);
                if (newValue == null) {
                    logger.debug("Removing row " + rowId.stringValue());
                    removeRowChangeEntry(rowChangeId, key, oldValue);
                } else {
                    logger.debug("Adding/replacing row " + rowId.stringValue() + " : "
                            + newValue);
                    addRowChangeEntry(rowChangeId, key, newValue, oldValue);
                }
            }
            if (no > 0) {
                currentRowChangeId = rowChangeId;
                logger.info("Got " + no + " new entries from log table for "
                        + fullSchemaName());
            }

            resultSet.close();
            connection.commit();

        } catch (SQLException e) {
            String message = "SQLException processing log table";
            logger.error(message, e);
            throw new RuntimeException(message);
        }

        if (commit) {
            commitRowChanges();
        }

        return no;
    }

    /**
     * Commits the result of updating the change log table. Empties the change
     * log table and saves the index to Oracle.
     *
     * @throws TaskException
     */
    private synchronized void commitRowChanges() throws TaskException {
        String update = "delete from " + logTable + " where row_change_id <= :1";
        logger.debug("Deleting from log table");
        try (var connection = getConnection();
             var updateStatement = connection.prepareStatement(update)) {
            updateStatement.setInt(1, currentRowChangeId);
            updateStatement.execute();
            logger.debug("Saving to Oracle");
            saveIndex();
            connection.commit();
        } catch (SQLException e) {
            String message = "SQLException processing log table";
            logger.error(message, e);
            throw new RuntimeException(message);
        }
    }

    /**
     * Converts a result set parameter index to a row value: smiles String, sdf
     * String or Mol byte array depending on index column type.
     *
     * @param parameterIndex
     * @param resultSet
     * @return
     */
    Object retrieveRowValueFromDatabase(int parameterIndex, ResultSet resultSet) {
        try {
            switch (indexColumnType) {
                case SMILES:
                    String smiles = resultSet.getString(parameterIndex);
                    if (resultSet.wasNull())
                        return null;
                    return smiles;
                case SDF:
                    Clob sdfClob = resultSet.getClob(parameterIndex);
                    if (resultSet.wasNull())
                        return null;
                    String sdf = SqlUtil.readClob(sdfClob);
                        sdfClob.free();
                    return sdf;
                case BINARY:
                    Blob molBlob = resultSet.getBlob(parameterIndex);
                    if (resultSet.wasNull())
                        return null;
                    byte[] molData = SqlUtil.readBlob(molBlob);
                    if (molBlob != null)
                        molBlob.free();
                    return molData;
            }
        } catch (SQLException e) {
            String message = "SQL error getting row value from database";
            logger.error(message, e);
            throw new RuntimeException(message);
        }

        return null;
    }

    /**
     * Adds a entry from the change log into the current index. If oldSmiles is
     * not null this is really and update.
     *
     * @param rowChangeId
     * @param rowid
     * @param newValue
     * @param oldValue
     */
    protected abstract void addRowChangeEntry(int rowChangeId, RowKey rowid,
                                              Object newValue, Object oldValue);

    /**
     * Removes an entry from the current index, based on a change log entry.
     *
     * @param rowChangeId
     * @param rowid
     * @param oldValue
     */
    protected abstract void removeRowChangeEntry(int rowChangeId, RowKey rowid,
                                                 Object oldValue);

    /**
     * @return the indexLoaded
     */
    boolean isIndexLoaded() {
        return indexLoaded;
    }

    /**
     * @param indexLoaded the indexLoaded to set
     */
    void setIndexLoaded(boolean indexLoaded) {
        this.indexLoaded = indexLoaded;
    }
}
