package com.cairn.rmi.common;

import java.io.Serializable;

// Needs to be OJVM source compatible

/**
 * Contains a batch or chunk of results.
 * 
 * @author gjones
 * 
 */
public class HitListChunk implements Serializable {

	private static final long serialVersionUID = 1000L;

	private static final int CHUNK_SIZE = 2000;

	// is the job finished, or is this the last batch of results?
	private final boolean finished;
	// rowids of histlist
	private final String[] hitlist;
	// optional scores
	private final double[] scores;
	// job number
	private final int jobNo;
	// pass any errors to consumer
	private Throwable exception;

	public HitListChunk(int jobNo, boolean finished, String[] hitlist,
			double[] scores) {
		super();
		this.jobNo = jobNo;
		this.finished = finished;
		this.hitlist = hitlist;
		this.scores = scores;
	}

	/**
	 * @return the finished
	 */
	public boolean isFinished() {
		return finished;
	}

	/**
	 * @return the hitlist
	 */
	public String[] getHitlist() {
		return hitlist;
	}

	/**
	 * @return the scores
	 */
	public double[] getScores() {
		return scores;
	}

	/**
	 * @return the chunkSize
	 */
	public static int getChunkSize() {
		return CHUNK_SIZE;
	}

	/**
	 * @return the jobNo
	 */
	public int getJobNo() {
		return jobNo;
	}

	/**
	 * @return the exception
	 */
	public Throwable getException() {
		return exception;
	}

	/**
	 * @param exception
	 *            the exception to set
	 */
	public void setException(Throwable exception) {
		this.exception = exception;
	}

}
