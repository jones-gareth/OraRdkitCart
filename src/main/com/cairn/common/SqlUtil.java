package com.cairn.common;

import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import oracle.jdbc.OracleConnection;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.log4j.Logger;

public class SqlUtil {

    private static final Logger logger = Logger.getLogger(SqlUtil.class);

    // Oracle maximum and minimum number type
    public static final double MAX_SQL_MUMBER = 1.0e125,
            MIN_SQL_MUMBER = 1.0e-130;

    /**
     * Closes a connection, but throws ModelException instead of SQL exception
     * on failure.
     *
     * @param connection
     * @throws ModelException
     */
    public static void closeConnection(Connection connection)
            throws ModelException {
        try {
            if (connection == null)
                logger.warn("connection is null");
            else
                connection.close();
        } catch (SQLException e) {
            String message = errorMessage(e);
            logger.error(message);
            throw new ModelException(message);
        }
    }

    /**
     * Commits a connection, but throws ModelException instead of SQL exception
     * on failure.
     *
     * @param connection
     * @throws ModelException
     */
    public static void commitConnection(Connection connection)
            throws ModelException {
        try {
            connection.commit();
        } catch (SQLException e) {
            String message = errorMessage(e);
            logger.error(message);
            throw new ModelException(message);
        }
    }

    /**
     * Rollbacks a connection, but throws ModelException instead of SQL
     * exception on failure.
     *
     * @param connection
     * @throws ModelException
     */
    public static void rollbackConnection(Connection connection)
            throws ModelException {
        try {
            connection.rollback();
        } catch (SQLException e) {
            String message = errorMessage(e);
            logger.error(message);
            throw new ModelException(message);
        }
    }

    /**
     * Return information about a column. First column is number 1.
     *
     * @param metaData
     * @param column
     * @throws SQLException
     */
    public static String columnInfoString(ResultSetMetaData metaData, int column)
            throws SQLException {

        String catalogName = metaData.getCatalogName(column);
        String schemaName = metaData.getSchemaName(column);
        String tableName = metaData.getTableName(column);
        String columnLabel = metaData.getColumnLabel(column);
        String columnName = metaData.getColumnName(column);

        String rtn = column
                + " "
                + columnLabel
                + ": "
                + StringUtils.join(new String[]{catalogName, schemaName,
                tableName, columnName}, '.');

        int columnType = metaData.getColumnType(column);
        String columnTypeName = metaData.getColumnTypeName(column);
        int precision = metaData.getPrecision(column);
        int scale = metaData.getScale(column);
        String columnClassName = metaData.getColumnClassName(column);

        rtn += " " + columnTypeName + " [" + columnType + "] Prec " + precision
                + " Scale " + scale + " " + columnClassName;

        return rtn;
    }

    /**
     * Returns information about a bound parameter. First parameter is 1. These
     * functions appear to be unsupported in the Oracle JDBC
     *
     * @param metaData
     * @param parameter
     * @return
     * @throws SQLException
     */
    public static String bindParameterInfoString(ParameterMetaData metaData,
                                                 int parameter) throws SQLException {

        int isNullable = metaData.isNullable(parameter);
        int precision = metaData.getPrecision(parameter);
        int scale = metaData.getScale(parameter);
        int parameterType = metaData.getParameterType(parameter);
        String parameterTypeName = metaData.getParameterTypeName(parameter);
        String parameterClassName = metaData.getParameterClassName(parameter);
        int parameterMode = metaData.getParameterMode(parameter);

        String nullableString = isNullable == ParameterMetaData.parameterNoNulls ? "NO"
                : isNullable == ParameterMetaData.parameterNullable ? "YES"
                : isNullable == ParameterMetaData.parameterNullableUnknown ? "UNK"
                : "ERROR";
        String modeString = parameterMode == ParameterMetaData.parameterModeIn ? "IN"
                : parameterMode == ParameterMetaData.parameterModeOut ? "OUT"
                : parameterMode == ParameterMetaData.parameterModeInOut ? "INOUT"
                : parameterMode == ParameterMetaData.parameterModeUnknown ? "UNK"
                : "ERROR";

        return parameter + " " + parameterTypeName + " [" + parameterType
                + "] Prec " + precision + " Scale " + scale + " "
                + parameterClassName + " Mode " + modeString + " nullable "
                + nullableString;
    }

    private static final Pattern bindNamePattern = Pattern.compile(":(\\w+)");

    /**
     * Parses an SQL Query for bind names.
     *
     * @param sqlQuery
     * @return
     */
    public static List<String> getBindNames(String sqlQuery) {
        Matcher matcher = bindNamePattern.matcher(sqlQuery);
        ArrayList<String> bindNames = new ArrayList<>();
        while (matcher.find()) {
            String bindName = matcher.group(1);
            bindNames.add(bindName);
        }
        return bindNames;
    }

    /**
     * Converts an map of bind parameters (indexed by parameter name) to a list
     * of objects in the order they appear in the sql query.
     *
     * @param sqlQuery
     * @param bindParamsMap
     * @return
     * @throws ModelException
     */
    public static List<Object> bindMapToArray(String sqlQuery,
                                              Map<String, Object> bindParamsMap) throws ModelException {
        List<String> bindNames = getBindNames(sqlQuery);
        List<Object> bindParams = new ArrayList<>();
        for (String bindName : bindNames) {
            Object bindParam = bindParamsMap.get(bindName);
            if (bindParam == null)
                bindParam = bindParamsMap.get(':' + bindName);
            if (bindParam == null)
                throw new ModelException(
                        "bindMapToArray: unable to get bind parameter object for :"
                                + bindName);
            bindParams.add(bindParam);
        }
        return bindParams;
    }

    /**
     * Opens a connection to an Oracle database.
     *
     * @param host
     * @param database
     * @param port
     * @param user
     * @param passwd
     * @return
     * @throws SQLException
     */
    public static Connection getConnection(String host, String database,
                                           String port, String user, String passwd) throws ModelException {
        if (StringUtils.isEmpty(port))
            port = "1521";
        String connStr = "jdbc:oracle:thin:@" + host + ":" + port + ":"
                + database;
        logger.info("Connecting to " + connStr);
        return getConnection(connStr, user, passwd);
    }

    /**
     * Opens a connection to an Oracle database.
     *
     * @param connStr
     * @param user
     * @param passwd
     * @return
     * @throws SQLException
     */
    public static Connection getConnection(String connStr, String user,
                                           String passwd) throws ModelException {
        if (user.equalsIgnoreCase("sys"))
            user = "sys as sysdba";
        try {
            DriverManager.registerDriver(new oracle.jdbc.OracleDriver());
            Connection connection = DriverManager.getConnection(connStr, user,
                    passwd);
            connection.setAutoCommit(false);
            return connection;
        } catch (SQLException e) {
            String message = errorMessage(e);
            logger.error(message);
            throw new ModelException(message);
        }
    }

    /**
     * @param ex
     * @return SQL error string
     */
    public static String errorMessage(SQLException ex) {
        return "SQL error: message: \"" + StringUtils.chomp(ex.getMessage())
                + "\" state: " + ex.getSQLState() + " code:"
                + ex.getErrorCode();
    }

    /**
     * Returns a string extracted from a clob.
     *
     * @param clob
     * @return
     * @throws ModelException
     */
    public static String readClob(Clob clob) throws ModelException {
        try {
            long length = clob.length();
            assert (length < Integer.MAX_VALUE);
            return clob.getSubString(1, (int) length);

        } catch (SQLException e) {
            String message = "Failed to read CLOB " + errorMessage(e);
            logger.error(message, e);
            throw new ModelException(message);
        }
    }

    /**
     * Write a string to a clob. The caller needs to free the clob
     *
     * @param clob
     * @param data
     * @throws ModelException
     */
    public static void writeClob(Clob clob, String data) throws ModelException {
        try {
            long length = clob.length();
            assert (length < Integer.MAX_VALUE);
            clob.setString(1, data);

        } catch (SQLException e) {
            String message = "Failed to write CLOB " + errorMessage(e);
            logger.error(message, e);
            throw new ModelException(message);
        }
    }

    /**
     * Reads data from a blob.
     *
     * @param blob
     * @return
     * @throws ModelException
     */
    public static byte[] readBlob(Blob blob) throws ModelException {
        if (blob == null)
            return null;
        try {
            long length = blob.length();
            assert (length < Integer.MAX_VALUE);
            return blob.getBytes(1, (int) length);
        } catch (SQLException e) {
            String message = "Failed to read BLOB " + errorMessage(e);
            logger.error(message, e);
            throw new ModelException(message);
        }
    }

    /**
     * Writes data to a blob. The calling function needs to make sure the blob
     * is freed.
     *
     * @param blob
     * @param data
     * @throws ModelException
     */
    public static void writeBlob(Blob blob, byte[] data) throws ModelException {
        try {
            long length = blob.length();
            assert (length < Integer.MAX_VALUE);
            blob.setBytes(1, data);
            logger.debug("Wrote blob of length " + blob.length());
        } catch (SQLException e) {
            String message = "Failed to write BLOB " + errorMessage(e);
            logger.error(message, e);
            throw new ModelException(message);
        }
    }

    /**
     * Writes a blob to the database.
     *
     * @param connection
     * @param query      sql to select the blob, should have only one column.
     * @param data
     * @throws ModelException
     */
    public static void writeBlob(Connection connection, String query,
                                 byte[] data) throws ModelException {
        try {
            PreparedStatement preparedStatement = connection
                    .prepareStatement(query);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                Blob blob = resultSet.getBlob(1);
                writeBlob(blob, data);
                blob.free();
            } else
                throw new ModelException("Unable to retrieve results for "
                        + query);
            resultSet.close();

        } catch (SQLException e) {
            String message = "Failed to read BLOB " + errorMessage(e);
            logger.error(message, e);
            throw new ModelException(message);
        }
    }

    /**
     * Writes a clob to the database.
     *
     * @param connection
     * @param query      sql to select the clob, should have only one column.
     * @param data
     * @throws ModelException
     */
    public static void writeClob(Connection connection, String query,
                                 String data) throws ModelException {
        try {
            PreparedStatement preparedStatement = connection
                    .prepareStatement(query);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                Clob clob = resultSet.getClob(1);
                writeClob(clob, data);
                clob.free();
            } else
                throw new ModelException("Unable to retrieve results for "
                        + query);
            resultSet.close();

        } catch (SQLException e) {
            String message = "Failed to read BLOB " + errorMessage(e);
            logger.error(message, e);
            throw new ModelException(message);
        }
    }

    /**
     * Takes a list of maps and updates a database table. Each map is keyed by
     * column names and the values are used to update the table. Returns the
     * number of rows updated.
     *
     * @param connection
     * @param tableName     the table to update
     * @param keyName       the primary key column
     * @param valuesMapList
     * @return
     * @throws ModelException
     */
    public static int executeUpdateMapList(Connection connection,
                                           String tableName, String keyName,
                                           List<Map<String, String>> valuesMapList) throws SQLException {

        try {
            tableName = tableName.toUpperCase();
            keyName = keyName.toUpperCase();

            if (valuesMapList.size() == 0)
                return 0;
            Set<String> keys = valuesMapList.get(0).keySet();
            List<String> columns = new ArrayList<>();
            List<String> terms = new ArrayList<>();
            for (String key : keys) {
                key = key.toUpperCase();
                if (key.equals(keyName))
                    continue;
                columns.add(key);
                terms.add(key + " = :" + key);
            }

            // generate update SQL
            String update = "update " + tableName + " set "
                    + StringUtils.join(terms, ",") + " where " + keyName
                    + " = :" + keyName;
            logger.debug(update);

            PreparedStatement statement = connection.prepareStatement(update);
            int updateCount = 0;

            // for each map in the list execute update
            for (Map<String, String> map : valuesMapList) {
                int i = 0;
                for (String column : columns) {
                    String value = map.get(column);
                    if (value != null && value.equals(""))
                        value = null;
                    i++;
                    statement.setString(i, value);
                    logger.debug("set parameter " + i + " column " + column
                            + " to " + value);
                }
                statement.setString(i + 1, map.get(keyName));
                statement.execute();
                updateCount += statement.getUpdateCount();
                logger.debug("Updated " + updateCount + " rows");
            }

            statement.close();

            return updateCount;
        } catch (SQLException e) {
            String message = errorMessage(e);
            logger.error(message);
            throw new SQLException(message);
        }
    }

    /**
     * Unwrap a jdbc connection to get the underlying Oracle connection.
     *
     * @param connection
     * @return
     * @throws SQLException
     */
    public static OracleConnection getOracleConnection(Connection connection)
            throws SQLException {
        if (OracleConnection.class.isAssignableFrom(connection.getClass())) {
            return (OracleConnection) connection;
        }

        try {
            return connection.unwrap(oracle.jdbc.OracleConnection.class);
        } catch (AbstractMethodError ex) {
            DatabaseMetaData metaData = connection.getMetaData();
            return (OracleConnection) metaData.getConnection();
        }
    }

    /**
     * Check to see if a number can fit in an oracle number datatype- chops it
     * if it can't.
     *
     * @param ok optional parameter- set to false if there are any problems.
     * @return
     */
    public static double checkSqlNumber(double number, MutableBoolean ok) {
        if (ok != null)
            ok.setValue(true);

        if (Double.isInfinite(number) || Double.isNaN(number)) {
            if (ok != null)
                ok.setValue(false);
            return 0.0;
        }

        if (number > MAX_SQL_MUMBER) {
            if (ok != null)
                ok.setValue(false);
            return MAX_SQL_MUMBER;
        }
        if (number < -MAX_SQL_MUMBER) {
            if (ok != null)
                ok.setValue(false);
            return -MAX_SQL_MUMBER;
        }

        if (number < MIN_SQL_MUMBER && number > -MIN_SQL_MUMBER) {
            return .0;
        }

        return number;
    }

}
