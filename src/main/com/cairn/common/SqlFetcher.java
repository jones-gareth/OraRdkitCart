package com.cairn.common;

import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.WordUtils;
import org.apache.log4j.Logger;


/**
 * Convenience class for fetching SQL data.
 *
 * @author Gareth Jones
 */
public class SqlFetcher implements Iterator<Object[]>, Iterable<Object[]> {

    private static final Logger logger = Logger.getLogger(SqlFetcher.class);

    private Object[] bindParams;
    private PreparedStatement preparedStatement;
    private ResultSetMetaData metaData;
    private ResultSet resultSet;
    private final Connection connection;

    private int nColumns;
    private SqlType[] columnTypes;
    private String[] columnNames;
    private final String query;

    private Set<Integer> blobParams, clobParams;
    private List<Blob> blobs;
    private List<Clob> clobs;

    private Object[] currentRow = null;

    /**
     * Prepares the query.
     *
     * @param connection
     * @param query
     * @throws ModelException
     */
    public SqlFetcher(Connection connection, String query) throws ModelException {
        this.query = query;
        this.connection = connection;
        try {
            preparedStatement = connection.prepareStatement(query);
        } catch (SQLException e) {
            String message = SqlUtil.errorMessage(e);
            logger.error(message, e);
            throw new ModelException(message);
        }

    }

    /**
     * Binds parameters.
     *
     * @throws ModelException
     */
    private void bindParams() throws ModelException {
        if (bindParams == null)
            return;
        try {

            for (int i = 0; i < bindParams.length; i++) {

                // Method toDatabaseMethod = null;

                // note the bind parameter meta data features are unimplemented
                // in Oracle
                if (false && logger.isDebugEnabled()) {
                    ParameterMetaData parameterMetaData = preparedStatement
                            .getParameterMetaData();
                    SqlUtil.bindParameterInfoString(parameterMetaData, +1);
                }

                Object param = bindParams[i];
                if (param == null) {
                    // to bind a null parameter we need the sqltype, but the
                    // Oracle JDBC does not support the ParameterMetaData calls,
                    // so just try varchar2.
                    // setObject() might work for nulls, but I'm not sure.
                    logger.debug("Binding a null parameter");
                    preparedStatement.setNull(i + 1, Types.VARCHAR);
                }

                // large object binding, for update/insert parameters (use
                // executeUpdate())
                else if (clobParams != null && clobParams.contains(i + 1)) {
                    logger.debug("Binding a clob parameter");
                    Clob clob = SqlUtil.getOracleConnection(connection).createClob();
                    SqlUtil.writeClob(clob, (String) param);
                    preparedStatement.setClob(i + 1, clob);
                    if (clobs == null)
                        clobs = new ArrayList<>();
                    clobs.add(clob);
                } else if (blobParams != null && blobParams.contains(i + 1)) {
                    logger.debug("Binding a blob parameter");
                    Blob blob = SqlUtil.getOracleConnection(connection).createBlob();
                    SqlUtil.writeBlob(blob, (byte[]) param);
                    preparedStatement.setBlob(i + 1, blob);
                    if (blobs == null)
                        blobs = new ArrayList<>();
                    blobs.add(blob);
                } else if (param instanceof Boolean) {
                    // Oracle has no boolean datatype so default to the strings
                    // "Y" and "N"
                    if ((Boolean) param)
                        preparedStatement.setString(i + 1, "Y");
                    else
                        preparedStatement.setString(i + 1, "N");
                } else if (param instanceof java.util.Date) {
                    java.sql.Timestamp date = new java.sql.Timestamp(
                            ((java.util.Date) param).getTime());
                    preparedStatement.setTimestamp(i + 1, date);
                }

                // else if ((toDatabaseMethod = ModelUtil.getMethod(param,
                // "toDatabase")) != null) {
                // Object obj = ModelUtil.invokeMethod(param, toDatabaseMethod);
                // logger
                // .debug("using object method toDatabase() to set parameter "
                // + i + " to " + obj);
                // preparedStatement.setObject(i + 1, obj);
                // }

                else if (param instanceof Double) {
                    // if necessary truncate to prevent numeric overflow or
                    // underflow
                    preparedStatement.setDouble(i + 1,
                            SqlUtil.checkSqlNumber((Double) param, null));
                } else if (param instanceof Integer) {
                    preparedStatement.setInt(i + 1, (Integer) param);
                } else if (param instanceof Long) {
                    preparedStatement.setLong(i + 1, (Long) param);
                } else if (Enum.class.isAssignableFrom(param.getClass())) {
                    // for enumerated types bind to enumerated type name.
                    preparedStatement.setString(i + 1, ((Enum<?>) param).name());
                } else {
                    preparedStatement.setObject(i + 1, param);
                }

            }
        } catch (SQLException e) {
            String message = SqlUtil.errorMessage(e);
            logger.error(message, e);
            throw new ModelException(message);
        }
    }

    /**
     * Executes DML (or DDL) and returns the number of rows affected
     *
     * @param bindParams
     * @return
     * @throws ModelException
     */
    public int executeUpdate(Object[] bindParams) throws ModelException {
        this.bindParams = bindParams;
        bindParams();
        try {
            int no = preparedStatement.executeUpdate();
            if (blobs != null) {
                for (Blob blob : blobs)
                    blob.free();
                blobs.clear();
            }
            if (clobs != null) {
                for (Clob clob : clobs)
                    clob.free();
                clobs.clear();
            }
            return no;
        } catch (SQLException e) {
            String message = SqlUtil.errorMessage(e);
            logger.error(message, e);
            throw new ModelException(message);
        }
    }

    /**
     * Executes a select statement (use fetchRow() to get the data)
     *
     * @param bindParams
     * @throws ModelException
     */
    public void executeQuery(Object[] bindParams) throws ModelException {
        this.bindParams = bindParams;
        bindParams();
        try {
            resultSet = preparedStatement.executeQuery();
            metaData = preparedStatement.getMetaData();
            nColumns = metaData.getColumnCount();
            columnTypes = new SqlType[nColumns];
            columnNames = new String[nColumns];

            for (int i = 0; i < nColumns; i++) {
                columnTypes[i] = SqlType.resultSetType(metaData, i + 1);
                columnNames[i] = metaData.getColumnName(i + 1);
                if (logger.isDebugEnabled()) {
                    logger.debug(SqlUtil.columnInfoString(metaData, i + 1));
                }
            }

        } catch (SQLException e) {
            String message = SqlUtil.errorMessage(e) + "[query: " + query + "]";
            logger.error(message, e);
            throw new ModelException(message);
        }
    }

    /**
     * @return a row of data
     * @throws ModelException
     */
    public Object[] fetchRow() throws ModelException {
        Object[] values = new Object[nColumns];

        try {
            if (!resultSet.next()) {
                resultSet.close();
                currentRow = null;
                return null;
            }

            for (int i = 0; i < nColumns; i++) {
                Object value=null;
                int columnNo = i + 1;

                switch (columnTypes[i]) {

                    case INT:
                        value = resultSet.getInt(columnNo);
                        if (resultSet.wasNull())
                            value = null;
                        break;

                    case DOUBLE:
                        value = resultSet.getDouble(columnNo);
                        if (resultSet.wasNull())
                            value = null;
                        break;

                    case BIGDECIMAL:
                        value = resultSet.getBigDecimal(columnNo);
                        break;

                    case STRING:
                        value = resultSet.getString(columnNo);
                        break;

                    case DATE:
                        Timestamp timestamp = resultSet.getTimestamp(columnNo);
                        if (timestamp != null)
                            value = new Date(timestamp.getTime());
                        break;

                    case BLOB:
                        Blob blob = resultSet.getBlob(columnNo);
                        if (!resultSet.wasNull())
                            value = SqlUtil.readBlob(blob);
                        if (blob != null)
                            blob.free();
                        break;

                    case CLOB:
                        Clob clob = resultSet.getClob(columnNo);
                        if (!resultSet.wasNull())
                            value = SqlUtil.readClob(clob);
                        if (clob != null)
                            clob.free();
                        break;

                    case RAW:
                        value = resultSet.getBytes(columnNo);
                        break;

                    case BOOLEAN:
                        value = resultSet.getBoolean(columnNo);
                        if (resultSet.wasNull())
                            value = null;
                        break;

                    case LONG:
                        value = resultSet.getLong(columnNo);
                        if (resultSet.wasNull())
                            value = null;
                        break;

                    case ROWID:
                        // note getRowId is not available for commons pool dhcp
                        // delegate
                        value = resultSet.getObject(columnNo);
                        if (resultSet.wasNull())
                            value = null;
                        break;

                    case UNKNOWN:
                    default:
                        value = resultSet.getObject(columnNo);
                        if (resultSet.wasNull())
                            value = null;
                        logger.debug("fetched result of type "
                                + (value == null ? "null" : value.getClass().getName())
                                + " for unknown column");
                        break;
                }

                values[i] = value;
            }

            currentRow = values;
            return values;

        } catch (SQLException e) {
            String message = SqlUtil.errorMessage(e);
            logger.error(message, e);
            throw new ModelException(message);
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see java.util.Iterator#hasNext()
     */
    @Override
    public boolean hasNext() {
        return fetchRow() != null;
    }

    /*
     * (non-Javadoc)
     *
     * @see java.util.Iterator#next()
     */
    @Override
    public Object[] next() {
        return currentRow;
    }

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Iterable#iterator()
     */
    @Override
    public Iterator<Object[]> iterator() {
        return this;
    }

    /**
     * @return a row as a map of values keyed by column name.
     * @throws ModelException
     */
    public Map<String, Object> fetchMap() throws ModelException {
        Object[] row = fetchRow();

        if (row == null)
            return null;

        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < row.length; i++)
            map.put(columnNames[i], row[i]);

        return map;
    }

    /**
     * close the result set
     */
    public void closeResultSet() {
        try {
            if (resultSet != null)
                resultSet.close();
        } catch (SQLException e) {
        }
    }

    /**
     * Close the prepared statement
     */
    public void closeStatement() {
        try {
            if (preparedStatement != null)
                preparedStatement.close();
        } catch (SQLException e) {
        }
    }

    /**
     * Tidy up
     */
    public void finish() {
        try {
            if (resultSet != null)
                resultSet.close();
            if (preparedStatement != null)
                preparedStatement.close();
        } catch (SQLException e) {
        }
    }

    // TODO replace with Phantom reference? see https://stackoverflow.com/questions/47762986/replacing-finalize-in-java
    @Override
    protected void finalize() {
        finish();
    }

    public String getQuery() {
        return query;
    }

    /**
     * Fetches a single value from the database.
     *
     * @param connection
     * @param query
     * @param bindParams
     * @return
     * @throws ModelException
     */
    public static Object fetchSingleValue(Connection connection, String query,
                                          Object[] bindParams) throws ModelException {
        SqlFetcher sql = new SqlFetcher(connection, query);
        if (sql.nColumns > 1)
            logger.warn("fetchSingleValue: multiple columns available");
        sql.executeQuery(bindParams);
        Object[] row = sql.fetchRow();
        if (row == null) {
            sql.finish();
            return null;
        }
        if (sql.fetchRow() != null) {
            logger.warn("fetchSingleValue: multiple rows available");
        }
        sql.finish();
        return row[0];
    }

    /**
     * Fetches a single row from the database.
     *
     * @param connection
     * @param query
     * @param bindParams
     * @return
     * @throws ModelException
     */
    public static Object[] fetchSingleRow(Connection connection, String query,
                                          Object[] bindParams) throws ModelException {
        SqlFetcher sql = new SqlFetcher(connection, query);
        sql.executeQuery(bindParams);

        Object[] row = sql.fetchRow();
        if (row == null) {
            sql.finish();
            return null;
        }
        if (sql.fetchRow() != null) {
            logger.warn("fetchSingleRow: multiple rows available", new Throwable());
        }
        sql.finish();
        return row;

    }

    public enum SelectForUpdateStatus {
        OK, NO_ROW, BUSY
    }

    public static class SelectForUpdateResult {
        private final Object[] row;
        private final SelectForUpdateStatus status;

        public SelectForUpdateResult(Object[] row, SelectForUpdateStatus status) {
            super();
            this.row = row;
            this.status = status;
        }

        /**
         * @return the row
         */
        public Object[] getRow() {
            return row;
        }

        /**
         * @return the status
         */
        public SelectForUpdateStatus getStatus() {
            return status;
        }

    }

    /**
     * Executes a select for update request. Returns OK if the row is locked,
     * NO_ROW if the row cannot be found and BUSY if the row is already locked
     * (only happens if the nowait option is used in the select statement).
     *
     * @param connection
     * @param query
     * @param bindParams
     * @return
     * @throws ModelException
     */
    public static SelectForUpdateResult selectSingleRowForUpdate(Connection connection,
                                                                 String query, Object[] bindParams) throws ModelException {
        SqlFetcher sql = new SqlFetcher(connection, query);

        Object[] row = null;
        try {
            sql.executeQuery(bindParams);
            row = sql.fetchRow();
        } catch (ModelException e) {
            if (e.getMessage().contains("ORA-00054")) {
                sql.finish();
                return new SelectForUpdateResult(row, SelectForUpdateStatus.BUSY);
            }
            throw e;
        }
        if (row == null) {
            sql.finish();
            return new SelectForUpdateResult(row, SelectForUpdateStatus.NO_ROW);
        }
        if (sql.fetchRow() != null) {
            logger.warn("fetchSingleRowForUpdate: multiple rows available");
        }
        sql.finish();
        return new SelectForUpdateResult(row, SelectForUpdateStatus.OK);
    }

    /**
     * Creates a list of beans from multiple rows from multiple rows.
     *
     * @param connection
     * @param query
     * @param bindParams
     * @param clazz            the bean class
     * @param columnToProperty
     * @return the list of bans
     */
    public static <T> List<T> importIntoList(Connection connection, String query,
                                             Object[] bindParams, Class<T> clazz, Map<String, String> columnToProperty) {
        List<T> array = new ArrayList<>();
        SqlFetcher fetcher = new SqlFetcher(connection, query);
        fetcher.executeQuery(bindParams);
        Object[] row;
        while ((row = fetcher.fetchRow()) != null) {
            T obj;
            try {
                obj = clazz.getDeclaredConstructor().newInstance();
            } catch (IllegalAccessException | InstantiationException | NoSuchMethodException | InvocationTargetException e) {
                throw new IllegalStateException(
                        "failed to create object of type " + clazz.getName());
            }
            fetcher.importRow(obj, columnToProperty, row);
            array.add(obj);
        }
        fetcher.finish();
        return array;
    }

    /**
     * Imports a single row into a class.
     *
     * @param connection
     * @param query
     * @param bindParams
     * @param bean
     * @param columnToProperty mapping of sql column names to bean properties (optional)
     * @return
     * @throws ModelException
     */
    public static boolean importSingleRow(Connection connection, String query,
                                          Object[] bindParams, Object bean, Map<String, String> columnToProperty)
            throws ModelException {
        SqlFetcher sql = new SqlFetcher(connection, query);
        sql.executeQuery(bindParams);

        Object[] row = sql.fetchRow();
        if (row == null) {
            sql.finish();
            return false;
        }
        if (sql.fetchRow() != null) {
            logger.warn("fetchSingleRow: multiple rows available");
        }

        sql.importRow(bean, columnToProperty, row);

        sql.finish();
        return true;

    }

    /**
     * convert Oracle column name to Java style e.g USER_ID to userId
     *
     * @param column
     * @return
     */
    public static String columnToJavaField(String column) {
        // convert Oracle column name to Java style e.g USER_ID to
        // userId
        String field = WordUtils.capitalize(column.toLowerCase(), '_');
        field = StringUtils.remove(field, '_');
        field = WordUtils.uncapitalize(field);
        return field;
    }

    /**
     * Imports a single row into a class.
     *
     * @param bean
     * @param columnToProperty columnToProperty mapping of sql column names to bean
     *                         properties (optional)
     * @param row
     * @throws ModelException
     */
    public void importRow(Object bean, Map<String, String> columnToProperty, Object[] row)
            throws ModelException {

        String beanClass = bean.getClass().getName();

        for (int colNo = 0; colNo < nColumns; colNo++) {
            String column = columnNames[colNo];
            Object value = row[colNo];
            if (value == null)
                continue;

            String name;
            if (columnToProperty != null && columnToProperty.containsKey(column))
                name = columnToProperty.get(column);
            else {
                // convert Oracle column name to Java style e.g USER_ID to
                // userId
                name = columnToJavaField(column);
            }

            try {
                Class<?> type = PropertyUtils.getPropertyType(bean, name);
                if (type == null) {
                    logger.warn("No property for " + name);
                    continue;
                }

                // Oracle has no boolean column so try to work with common
                // string representations.
                if ((type == Boolean.TYPE || type == Boolean.class)
                        && value instanceof String) {
                    String str = (String) value;
                    if (str.equals("1") || str.equals("true")
                            || str.equalsIgnoreCase("Y")) {
                        value = true;
                    } else if (str.equals("0") || str.equals("false")
                            || str.equalsIgnoreCase("N")) {
                        value = false;
                    }
                }

                // BigDecimal to integer
                if ((type == Integer.TYPE || type == Integer.class)
                        && value instanceof BigDecimal)
                    value = ((BigDecimal) value).intValueExact();

                // BigDecimal to double
                if ((type == Double.TYPE || type == Double.class)
                        && value instanceof BigDecimal)
                    value = ((BigDecimal) value).doubleValue();

                // long to integer
                if ((type == Integer.TYPE || type == Integer.class)
                        && value instanceof Long)
                    value = CommonUtils.longToInt((Long) value);

                if (PropertyUtils.isWriteable(bean, name)) {
                    PropertyUtils.setProperty(bean, name, value);
                    logger.debug("Set property " + name + " on bean " + beanClass
                            + " to value " + value);
                } else {
                    logger.warn("unable to write column " + column + " property " + name
                            + " to bean " + beanClass);
                }
            } catch (Exception e) {
                String message = "error writing column " + column + " property " + name
                        + " to bean " + beanClass;
                logger.error(message, e);
                throw new ModelException(message);
            }
        }

    }

    /**
     * Fetches a single column from the database.
     *
     * @param connection
     * @param query
     * @param bindParams
     * @return
     * @throws ModelException
     */
    public static List<Object> fetchSingleColumn(Connection connection, String query,
                                                 Object[] bindParams) throws ModelException {
        SqlFetcher sql = new SqlFetcher(connection, query);
        sql.executeQuery(bindParams);
        if (sql.nColumns > 1)
            logger.warn("fetchSingleValue: multiple columns available");

        List<Object> values = new ArrayList<>();
        Object[] row;
        while ((row = sql.fetchRow()) != null) {
            values.add(row[0]);
        }
        sql.finish();
        return values;
    }

    /**
     * Executes sql to create a lookup. The sql should return two columns: an id
     * and associated label. For example
     * "select target_id, target_name from targets" will create a hash mapping
     * ids to target names.
     *
     * @param connection
     * @param query
     * @param bindParams
     * @return
     * @throws ModelException
     */
    public static Map<String, String> fetchIdLookup(Connection connection, String query,
                                                    Object[] bindParams) throws ModelException {

        SqlFetcher sql = new SqlFetcher(connection, query);
        sql.executeQuery(bindParams);
        if (sql.nColumns > 2)
            logger.warn("fetchIdLookup: multiple columns available");
        Object[] row;
        Map<String, String> lookup = new HashMap<>();
        while ((row = sql.fetchRow()) != null) {
            String id = objectToString(row[0]);
            String value = objectToString(row[1]);
            lookup.put(id, value);
        }
        sql.finish();
        return lookup;
    }

    /**
     * Executes sql to create a lookup. The sql should return two columns: an id
     * and associated label. For example
     * "select target_id, target_name from targets" will create a hash mapping
     * ids to target names.
     *
     * @param connection
     * @param query
     * @param bindParams
     * @return
     * @throws ModelException
     */
    public static Map<Integer, String> fetchIntIdLookup(Connection connection,
                                                        String query, Object[] bindParams) throws ModelException {

        SqlFetcher sql = new SqlFetcher(connection, query);
        sql.executeQuery(bindParams);
        if (sql.nColumns > 2)
            logger.warn("fetchIdLookup: multiple columns available");
        Object[] row;
        Map<Integer, String> lookup = new HashMap<>();
        while ((row = sql.fetchRow()) != null) {
            int id = objectToInt(row[0]);
            String value = objectToString(row[1]);
            lookup.put(id, value);
        }
        sql.finish();
        return lookup;
    }

    /**
     * Executes a single DML or DDL statement.
     *
     * @param connection
     * @param query
     * @param bindParams
     * @return
     * @throws ModelException
     */
    public static int updateCommand(Connection connection, String query,
                                    Object[] bindParams) throws ModelException {
        SqlFetcher sql = new SqlFetcher(connection, query);
        int no = sql.executeUpdate(bindParams);
        sql.finish();
        return no;
    }

    /**
     * Converts an object returned by these routines to an Integer (null safe)
     *
     * @param obj
     * @return
     * @throws ModelException
     */
    public static Integer objectToIntObj(Object obj) throws ModelException {
        if (obj == null)
            return null;
        return objectToInt(obj);
    }

    /**
     * Converts an object returned by these routines to a boolean. Oracle does
     * not have a boolean column type, so we expect a string value and return
     * true if its 1, yes, true or y.
     *
     * @param obj
     * @return
     * @throws ModelException
     */
    public static Boolean objectToBoolean(Object obj) throws ModelException {
        if (obj == null)
            return null;
        String str = objectToString(obj);
        return str.equalsIgnoreCase("true") || str.equalsIgnoreCase("y")
                || str.equalsIgnoreCase("yes") || str.equalsIgnoreCase("1");
    }

    /**
     * Converts an object returned by these routines to an int
     *
     * @param obj
     * @return
     * @throws ModelException
     */
    public static int objectToInt(Object obj) throws ModelException {
        if (obj instanceof BigDecimal)
            return ((BigDecimal) obj).intValue();
        if (obj instanceof Integer)
            return (Integer) obj;
        if (obj instanceof Double)
            return ((Double) obj).intValue();
        if (obj instanceof String)
            return Integer.parseInt((String) obj);
        if (obj instanceof Long)
            return CommonUtils.longToInt((Long) obj);
        throw new ModelException(
                "Can't convert object of type " + obj.getClass().getName() + " to int");
    }

    /**
     * Converts an object returned by these routines to an String
     *
     * @param obj
     * @return
     * @throws ModelException
     */
    public static String objectToString(Object obj) throws ModelException {
        if (obj == null)
            return null;
        return obj.toString();
    }

    /**
     * Converts an object returned by these routines to a Double (null safe)
     *
     * @param obj
     * @return
     * @throws ModelException
     */
    public static Double objectToDoubleObj(Object obj) throws ModelException {
        if (obj == null)
            return null;
        return objectToDouble(obj);
    }

    /**
     * Converts an object returned by these routines to a double.
     *
     * @param obj
     * @return
     * @throws ModelException
     */
    public static double objectToDouble(Object obj) throws ModelException {
        if (obj instanceof BigDecimal)
            return ((BigDecimal) obj).doubleValue();
        if (obj instanceof Double)
            return (Double) obj;
        // if (obj instanceof Double)
        // return ((Double) obj).doubleValue();
        if (obj instanceof String)
            return Double.parseDouble((String) obj);
        throw new ModelException("Can't convert object of type "
                + obj.getClass().getName() + " to double");

    }

    /**
     * Returns the next value for this sequence.
     *
     * @param connection
     * @param sequenceName
     * @return
     * @throws ModelException
     */
    public static int getNextSequence(Connection connection, String sequenceName)
            throws ModelException {
        String query = "select " + sequenceName + ".nextVal from dual";
        Object obj = fetchSingleValue(connection, query, null);
        return SqlFetcher.objectToInt(obj);
    }

    /**
     * @param connection
     * @param idName
     * @return
     * @throws ModelException
     */
    public static int getNextId(Connection connection, String idName)
            throws ModelException {
        String sequenceName = idName + "_seq";
        return getNextSequence(connection, sequenceName);
    }

    /**
     * Converts an Object list (e.g. returned from fetchSingleColumn) to a
     * String list
     *
     * @param input
     * @return
     * @throws ModelException
     */
    public static List<String> objectListToStringList(List<Object> input)
            throws ModelException {
        List<String> rtn = new ArrayList<>();
        for (Object obj : input) {
            rtn.add(objectToString(obj));
        }
        return rtn;
    }

    /**
     * Converts an Object list (e.g. returned from fetchSingleColumn) to a
     * Double list
     *
     * @param input
     * @return
     * @throws ModelException
     */
    public static List<Double> objectListToDoubleList(List<Object> input)
            throws ModelException {
        List<Double> rtn = new ArrayList<>();
        for (Object obj : input) {
            rtn.add(objectToDoubleObj(obj));
        }
        return rtn;
    }

    /**
     * Converts an Object list (e.g. returned from fetchSingleColumn) to a Date
     * list
     *
     * @param input
     * @return
     * @throws ModelException
     */
    public static List<Date> objectListToDateList(List<Object> input)
            throws ModelException {
        List<Date> rtn = new ArrayList<>();
        for (Object obj : input) {
            rtn.add((Date) obj);
        }
        return rtn;
    }

    /**
     * Converts an Object list (e.g. returned from fetchSingleColumn) to an
     * Integer list
     *
     * @param input
     * @return
     * @throws ModelException
     */
    public static List<Integer> objectListToIntList(List<Object> input)
            throws ModelException {
        List<Integer> rtn = new ArrayList<>();
        for (Object obj : input) {
            rtn.add(objectToIntObj(obj));
        }
        return rtn;
    }

    /**
     * Marks an update parameter as a clob (indexed from 1)
     *
     * @param no
     */
    public void setClobParameter(int no) {
        if (clobParams == null)
            clobParams = new HashSet<>();
        clobParams.add(no);
    }

    /**
     * Marks an update parameter as a blob (indexed from 1)
     *
     * @param no
     */
    public void setBlobParameter(int no) {
        if (blobParams == null)
            blobParams = new HashSet<>();
        blobParams.add(no);
    }

    /**
     * @return the columnNames
     */
    public String[] getColumnNames() {
        return columnNames;
    }

}
