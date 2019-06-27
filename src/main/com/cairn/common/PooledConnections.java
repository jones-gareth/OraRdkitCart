package com.cairn.common;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import oracle.jdbc.OracleDriver;

import org.apache.commons.dbcp2.ConnectionFactory;
import org.apache.commons.dbcp2.DriverManagerConnectionFactory;
import org.apache.commons.dbcp2.PoolableConnection;
import org.apache.commons.dbcp2.PoolableConnectionFactory;
import org.apache.commons.dbcp2.PoolingDriver;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.log4j.Logger;

/**
 * Uses Jakarta Commons DBCP project to create connection pools.
 * 
 * @author Gareth Jones
 * 
 */
public class PooledConnections {
	private static final Logger logger = Logger.getLogger(PooledConnections.class);
	private static volatile boolean driverRegistered = false;

	// Track pools by user- we'll need a pool for each user/database.
	private static final Map<String, GenericObjectPool<PoolableConnection>> pools = new HashMap<>();

	/**
	 * Register Oracle Driver
	 */
	private static void registerDriver() {
		if (driverRegistered)
			return;
		try {
			DriverManager.registerDriver(new OracleDriver());
		} catch (SQLException ex) {
			logger.fatal("Unable to load Oracle driver", ex);
		}
		driverRegistered = true;
	}

	/**
	 * Create pool for a user
	 * 
	 * @param user
	 * @param password
	 */
	private static void createConnectionPool(String connStr, String user, String password) {
		logger.debug("creating conection pool for user " + user + " at " + connStr);
		ConnectionFactory connectionFactory = new DriverManagerConnectionFactory(connStr,
				user, password);
		PoolableConnectionFactory poolableConnectionFactory = new PoolableConnectionFactory(
				connectionFactory, null);
		GenericObjectPool<PoolableConnection> connectionPool = new GenericObjectPool<>(
				poolableConnectionFactory);
		poolableConnectionFactory.setPool(connectionPool);

		// pool configuration (perhaps should be in external config).
		poolableConnectionFactory.setDefaultAutoCommit(false);
		poolableConnectionFactory.setValidationQuery("select 1 from dual");
		connectionPool.setTestOnBorrow(true);
		connectionPool.setTestOnReturn(true);
		connectionPool.setTestWhileIdle(true);
		connectionPool.setMaxTotal(20);
		connectionPool.setMaxIdle(10);
		connectionPool.setTimeBetweenEvictionRunsMillis(60000);
		connectionPool.setMinEvictableIdleTimeMillis(900000);

		String poolName = user + "@" + connStr;
		//PoolingDriver.setAccessToUnderlyingConnectionAllowed(true);
		PoolingDriver driver = new PoolingDriver();
		driver.registerPool(poolName, connectionPool);
		pools.put(poolName, connectionPool);
	}

	/**
	 * @param user
	 * @param password
	 * 
	 * @return a database connection for this user and database.
	 */
	public static Connection getConnection(String connStr, String user, String password)
			throws ModelException {
		if (!driverRegistered)
			registerDriver();

		String poolName = user + "@" + connStr;
		if (!pools.containsKey(poolName)) {
			createConnectionPool(connStr, user, password);
			logger.debug("creating pool " + poolName);
		}
		try {
			logger.debug("connecting to pool " + poolName);
			Connection conn = DriverManager.getConnection("jdbc:apache:commons:dbcp:"
					+ poolName);
			conn.setAutoCommit(false);
			return conn;
		} catch (SQLException ex) {
			String message = "Unable to connect to database: " + SqlUtil.errorMessage(ex);
			logger.fatal(message, ex);
			throw new ModelException(message);
		}
	}

	/**
	 * @return the pools
	 */
	public static Map<String, GenericObjectPool<PoolableConnection>> getPools() {
		return pools;
	}

}
