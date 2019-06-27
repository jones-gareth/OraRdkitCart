package com.cairn.rmi.server;

import com.cairn.common.ModelException;
import com.cairn.common.PooledConnections;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.jdbc.JDBCAppender;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Library of utilities to assist in implementing the RMI server.
 *
 * @author Gareth Jones
 */
public class TaskUtil {
    public static final String USER = "C$CSCHEM1";
    private static volatile String password, database, host;
    private static volatile int port;
    private static final Logger logger = Logger.getLogger(TaskUtil.class);
    private static volatile DatabaseAppender databaseAppender;
    private static volatile boolean useDatabaseAppender = false;

    /**
     * Create a unique key for an index of a given type.
     *
     * @param ownerName
     * @param tableName
     * @return index lookup key from table and owner.
     */
    public static String getIndexKey(String ownerName, String tableName, String columnName) {
        tableName = tableName.toUpperCase();
        ownerName = ownerName.toUpperCase();
        columnName = columnName.toUpperCase();
        return ownerName + "." + tableName + "." + columnName;
    }

    /**
     * Sets the credentials that the rmi server can use to connect to Oracle.
     *
     * @param password
     * @param database
     * @param host
     * @param port
     */
    public static void setCredentials(String password, String database, String host,
                                      int port) {
        TaskUtil.password = password;
        TaskUtil.database = database;
        TaskUtil.host = host;
        if (port <= 0)
            port = 1521;
        TaskUtil.port = port;

    }

    public static boolean isUseDatabaseAppender() {
        return useDatabaseAppender;
    }

    /**
     * Can log log4J messages of priority >= warn to a message table.
     *
     * @param useDatabaseAppender
     */
    public static void setUseDatabaseAppender(boolean useDatabaseAppender) {
        TaskUtil.useDatabaseAppender = useDatabaseAppender;
    }

    private static boolean driverRegistered = false;


    public static Connection getConnection() {
        return getConnection(true);
    }

    /**
     * @return an Oracle connection from the set credentials
     * @throws SQLException
     */
    public static Connection getConnection(boolean pooled) {
        try {
            // Note make sure that the jdbc logger doesn't attempt to log in
            // here- or we'll go into a loop!
            Logger rootLogger = Logger.getRootLogger();
            if (databaseAppender != null)
                rootLogger.removeAppender(databaseAppender);
            String connStr = getConnectionString();
            Connection connection;
            if (pooled) {
                try {
                    connection = PooledConnections.getConnection(connStr, USER, password);
                } catch (ModelException e) {
                    logger.error("ModelException getting Pooled Connection", e);
                    throw new SQLException(e);
                }
            } else {
                if (!driverRegistered) {
                    driverRegistered = true;
                    DriverManager.registerDriver(new oracle.jdbc.OracleDriver());
                }

                connection = DriverManager.getConnection(connStr, USER, password);
                connection.setAutoCommit(false);
            }
            // connection.setAutoCommit(true);
            if (databaseAppender != null)
                rootLogger.addAppender(databaseAppender);
            return connection;
        } catch (SQLException e) {
            String message = "SQLException getting pooled connection";
            logger.error(message, e);
            throw new RuntimeException(message);
        }
    }

    public static void closeConnection(Connection connection) {
        try {
            connection.rollback();
            connection.close();
        } catch (SQLException e) {
            String message = "SQLException closing connection";
            logger.error(message, e);
            throw new RuntimeException(message);
        }
    }

    /**
     * @return appropriate connection string
     */
    private static String getConnectionString() {
        logger.trace("Connecting to " + database + " using thin");
        return "jdbc:oracle:thin:@" + host + ":" + port + ":" + database;
    }

    /**
     * Create a JDBC appender- require access to table message_table. Does
     * nothing if useDatabaseAppender is false.
     */
    public static synchronized void addDatabaseAppender() {
        if (!useDatabaseAppender)
            return;
        if (databaseAppender != null)
            return;
        Logger rootLogger = Logger.getRootLogger();
        databaseAppender = new DatabaseAppender();
        databaseAppender.setSql("insert into message_table (time_logged, message) "
                + "values (systimestamp, '%-5p %c - %m  [%t] (%F:%L)')");
        databaseAppender.setThreshold(Level.WARN);
        rootLogger.addAppender(databaseAppender);
    }

    public static String getDatabase() {
        return database;
    }

    public static String getHost() {
        return host;
    }

    public static int getPort() {
        return port;
    }
}

/**
 * Extension of JDBC appender to get the connection using our credentials.
 *
 * @author Gareth Jones
 */
class DatabaseAppender extends JDBCAppender {

    @Override
    protected Connection getConnection() {
        return TaskUtil.getConnection();
    }

    @Override
    protected void closeConnection(Connection con) {
        TaskUtil.closeConnection(con);
    }

}
