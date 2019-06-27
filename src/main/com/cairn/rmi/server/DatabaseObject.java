package com.cairn.rmi.server;

import com.cairn.common.SqlUtil;
import com.cairn.rmi.TaskException;
import org.apache.log4j.Logger;

import java.io.*;
import java.sql.*;

/**
 * A class to serialize Java objects to persistent storage.
 * 
 * Objects are serialized and stored a table.
 * 
 * Requires the table java_objects and sequence java_object_id_seq:
 * 
 * @author Gareth Jones
 * 
 */
public class DatabaseObject {

	/**
	 * SQL for creating an entry in the objects table
	 */
	static final private String CREATE_SQL = "insert into java_objects "
			+ "(java_object_id, name, value) "
			+ " values (java_object_id_seq.nextval, ?,  empty_blob())";
	/**
	 * SQL for fetching an entry from the objects table
	 */
	static final private String FETCH_SQL = "select value from java_objects where name = ? for update";

	/**
	 * SQL for deleting an java object from Oracle.
	 */
	static final private String DELETE_SQL = "delete from java_objects where name = ?";

	private static final Logger logger = Logger.getLogger(DatabaseObject.class);

	private final Connection connection;

	private Blob blob;
	private final String name;
	private Object value;
	private PreparedStatement blobStatement;

	/**
	 * Sets the database connection and key that will be used to reference the
	 * object.
	 * 
	 * @param connection
	 * @param name
	 */
	public DatabaseObject(Connection connection, String name) {
		this.name = name;
		this.connection = connection;
	}

	/**
	 * Gets the existing blob stored in oracle.
	 * 
	 * @return
	 * @throws SQLException
	 */
	private Blob getExistingBlobLocator() throws SQLException {
		Connection connection = SqlUtil.getOracleConnection(this.connection);
		PreparedStatement stmt = connection.prepareStatement(FETCH_SQL);
		stmt.setString(1, name);
		boolean ok = stmt.execute();
		if (!ok)
			return null;
		ResultSet resultSet = stmt.getResultSet();
		if (!resultSet.next()) {
			stmt.close();
			return null;
		}
		blobStatement = stmt;
		blob = resultSet.getBlob(1);
		logger.debug("got existing blob handle for " + name);
		return blob;
	}

	/**
	 * Creates a new row to store the object and returns a blob locator. Will
	 * throw an exception if that row exists.
	 * 
	 * @return
	 * @throws SQLException
	 */
	private Blob getNewBlobLocator() throws SQLException {
		logger.debug("creating new row for " + name);
		PreparedStatement stmt = connection.prepareStatement(CREATE_SQL);
		stmt.setString(1, name);
		stmt.execute();
		stmt.close();
		return getExistingBlobLocator();
	}

	/**
	 * Either returns a blob locator for an existing row or creates a new one.
	 * 
	 * @return
	 * @throws SQLException
	 */
	private Blob getBlobLocator() throws SQLException {
		Blob blob = getExistingBlobLocator();
		if (blob == null)
			return getNewBlobLocator();
		return blob;
	}

	/**
	 * @return the serialized object stored in the blob. Retrieval is only
	 *         performed once.
	 * 
	 * @throws Exception
	 */
	public Object getValue() throws TaskException {
		if (value != null)
			return value;

		try {
			getBlobLocator();

			if (blob.length() == 0) {
				logger.debug("getValue: empty object");
				return null;
			}

			try {
				InputStream in = blob.getBinaryStream();
				ObjectInputStream objectIn = new ObjectInputStream(
						new BufferedInputStream(in));
				value = objectIn.readObject();
				objectIn.close();
				in.close();
			} catch (IOException e) {
				String message = "IOException getting database object value";
				logger.error(message, e);
				throw new TaskException(message);
			} catch (ClassNotFoundException e) {
				String message = "ClassNotFoundException getting database object value";
				logger.error(message, e);
				throw new TaskException(message);
			}
			logger.debug("getValue: Retrieved object of length " + blob.length());

			blobStatement.close();
			blob.free();
			return value;
		} catch (SQLException e) {
			String message = "SQLException processing getting datablase object value";
			logger.error(message, e);
			throw new RuntimeException(message);
		}
	}

	/**
	 * Sets the object. Note you need to call save() to store the object in
	 * Oracle.
	 * 
	 * @param value
	 */
	public void setValue(Object value) {
		this.value = value;
	}

	/**
	 * Saves the Object to Oracle.
	 * 
	 * @throws Exception
	 */
	public void save() throws TaskException {
		try {
			getBlobLocator();

			OutputStream os = blob.setBinaryStream(1L);
			try {
				ObjectOutputStream objectOut = new ObjectOutputStream(
						new BufferedOutputStream(os));
				objectOut.writeObject(value);
				objectOut.flush();
				objectOut.close();
				os.close();
				logger.debug("save: Saved object of length " + blob.length());

			} catch (IOException e) {
				String message = "IOException saving database object value";
				logger.error(message, e);
				throw new TaskException(message);
			}

			blob.free();
			blobStatement.close();
		} catch (SQLException e) {
			String message = "SQLException saving database object";
			logger.error(message, e);
			throw new RuntimeException(message);
		}

	}

	/**
	 * Removes this Java object from Oracle
	 * 
	 * @throws SQLException
	 */
	public void delete() {
		try {
			PreparedStatement stmt = connection.prepareStatement(DELETE_SQL);
			stmt.setString(1, name);
			stmt.execute();
			stmt.close();
		} catch (SQLException e) {
			String message = "SQLException deleting java object";
			logger.error(message, e);
			throw new RuntimeException(message);
		}

	}

}
