package com.cairn.rmi.index;

import java.io.Serializable;
import java.util.Arrays;

import oracle.sql.ROWID;

/**
 * Class to implement keys for Table indexes. Wraps rowid for use as a hash key.
 * 
 * @author Gareth Jones
 * 
 */
public class RowKey implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1000L;
	private final byte[] rowId;

	/**
	 * Create from bytes
	 * 
	 * @param rowId
	 */
    private RowKey(byte[] rowId) {
		this.rowId = rowId;
	}

	/**
	 * Create from Oracle ROWID
	 * 
	 * @param oracleRowid
	 */
	public RowKey(ROWID oracleRowid) {
		this(oracleRowid.getBytes());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(rowId);
		return result;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		RowKey other = (RowKey) obj;
		return Arrays.equals(rowId, other.rowId);
	}

	public byte[] getRowId() {
		return rowId;
	}

	public ROWID getROWID() {
		ROWID oracleRowId = new ROWID();
		oracleRowId.setShareBytes(rowId);
		return oracleRowId;
	}

	/*
	 * Creates a string representation that can be passed back to PL/SQL. As far
	 * as I know this is the only representation that can be passed back to
	 * PL/SQL for rowids (byte[] and ROWID do not work).
	 * 
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		// return getROWID().stringValue();
		return new String(rowId);
	}
}
