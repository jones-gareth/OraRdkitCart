package com.cairn.common;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;

import org.apache.log4j.Logger;

/**
 * Classify SQL types.
 * 
 * @author Gareth Jones
 * 
 */
public enum SqlType {
	INT, DOUBLE, BIGDECIMAL, STRING, DATE, BLOB, CLOB, RAW, BOOLEAN, LONG, UNKNOWN, ROWID;

	private static final Logger logger = Logger.getLogger(SqlType.class);

	public static SqlType resultSetType(ResultSetMetaData metaData, int columnNo)
			throws ModelException {

		try {
			int sqlType = metaData.getColumnType(columnNo);

			switch (sqlType) {
			case Types.CHAR:
			case Types.VARCHAR:
			case Types.LONGVARCHAR:
				return STRING;
			case Types.BIT:
				return BOOLEAN;
			case Types.TINYINT:
			case Types.SMALLINT:
			case Types.INTEGER:
				return INT;
			case Types.BIGINT:
				return LONG;
			case Types.FLOAT:
			case Types.DOUBLE:
				return DOUBLE;
			case Types.NUMERIC:
			case Types.DECIMAL:
				int scale = metaData.getScale(columnNo);
				int precision = metaData.getPrecision(columnNo);
				// if If both scale and precision are omitted, the number is
				// treated as a floating-point number. We should use bigdecimal
				// here in case the number is an int
				if (scale == 0 && precision == 0)
					return BIGDECIMAL;
				// Oracle INTEGER default, if scale is omitted
				if (scale == 0) {
					// max java int is 10^31 which is 2.14E10, so put columns
					// with precision 10 or more into a long.
					if (precision < 10)
						return INT;
					else
						return LONG;
				}
				// Oracle NUMBER default
				// if (scale == -127)
				// return DOUBLE;
				// logger.warn("creating Big Decimal Object scale " + scale
				// + " precision " + precision);

				// stick with BIGDECIMAL as int to double conversion is more
				// exact.
				return BIGDECIMAL;
			case Types.DATE:
			case Types.TIMESTAMP:
			case Types.TIME:
				return DATE;
			case Types.BINARY:
			case Types.VARBINARY:
				return RAW;
			case Types.BLOB:
				return BLOB;
			case Types.CLOB:
				return CLOB;
				// note I don't think that Oracle 11g urowid type is caught here
			case Types.ROWID:
				return ROWID;

			default:
				String typeName = metaData.getColumnTypeName(columnNo);
				String className = metaData.getColumnClassName(columnNo);

				logger.debug("sqlTypeToDatabase: Unable to get type for SQL type "
						+ sqlType + " " + typeName + " class " + className);
				return UNKNOWN;
			}

		} catch (SQLException e) {
			String message = "SQL Error";
			logger.error(message, e);
			throw new ModelException(message);
		}
	}
}
