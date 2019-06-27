package com.cairn.rmi.index;

import java.util.BitSet;
import java.util.concurrent.atomic.AtomicInteger;

import org.RDKit.ExplicitBitVect;
import org.apache.log4j.Logger;

import com.cairn.common.SubstructureMatcher;
import com.cairn.rmi.server.TaskJobResults;

/**
 * Uses a thread pool to perform atom by atom substructure matches.
 * 
 * This class consists of a queue to hold requests for substructure searches and
 * a thread pool to evaluate those requests using producer/consumer patter.
 * 
 * @author gjones
 * 
 */
public class SubstructureSearchPool extends TaskPool {
	private static final Logger logger = Logger.getLogger(SubstructureSearchPool.class);

	private static volatile int nThreads = 1;
	private static volatile boolean useSubstructureSearchPool = false;

	/**
	 * Data structure to store search requests
	 */
	private static class SubSearchQueueItem extends QueueItem {
		final String target;
		final RowKey rowKey;
		final boolean trusted;
		final BitSet targetFingerprint;

		private SubSearchQueueItem(int jobNo, RowKey rowKey, String target, boolean trusted, BitSet targetFingerprint) {
			super(jobNo);
			this.rowKey = rowKey;
			this.target = target;
			this.trusted = trusted;
			this.targetFingerprint = targetFingerprint;
		}
	}

	/**
	 * A class to store information about each search
	 */
	private static class SubSearchTaskJobInfo extends TaskJobInfo {
		private final TaskJobResults taskJobResults;
		private final SubstructureMatcher matcher;
		private final int maxHits;
		private final AtomicInteger nHits = new AtomicInteger();

		private SubSearchTaskJobInfo(TaskJobResults taskJobResults,
				SubstructureMatcher matcher, int maxHits) {
			super(taskJobResults.getJobNo());
			this.taskJobResults = taskJobResults;
			this.matcher = matcher;
			this.maxHits = maxHits;
		}

		private boolean maxHitsObtained() {
			if (maxHits <= 0)
				return false;
			return nHits.get() >= maxHits;
		}

	}

	private SubstructureSearchPool() {
		super("subSearchThreadPool", nThreads);
		if (nThreads == 1)
			throw new IllegalStateException(
					"Pointless creation of substructure search thread pool with only one thread");
	}

	private static volatile SubstructureSearchPool substructureSearchPool;

	/**
	 * Get the substructure search pool.
	 * 
	 * @return
	 */
	public static synchronized SubstructureSearchPool getInstance() {
		if (!useSubstructureSearchPool)
			throw new IllegalStateException(
					"Substructure search pool is not enabled!");

		if (substructureSearchPool != null)
			return substructureSearchPool;
		substructureSearchPool = new SubstructureSearchPool();
		substructureSearchPool.start();
		return substructureSearchPool;
	}

	/**
	 * Register a new search
	 * 
	 * @param taskJobResults
	 * @param matcher
	 * @param maxHits
	 */
	public void startSearch(TaskJobResults taskJobResults,
			SubstructureMatcher matcher, int maxHits) {
		SubSearchTaskJobInfo taskJobInfo = new SubSearchTaskJobInfo(
				taskJobResults, matcher, maxHits);
		super.startJob(taskJobInfo);
	}

	/**
	 * Adds a search request to the queue
	 * 
	 * @param jobNo
	 * @param rowKey
	 * @param target
	 * @return false if the maximum number of hits has been obtained.
	 */
	public boolean submitMolSearch(int jobNo, RowKey rowKey, String target, boolean trusted, BitSet targetFingerprint) {
		SubSearchQueueItem item = new SubSearchQueueItem(jobNo, rowKey, target, trusted, targetFingerprint);
		logger.trace("Submitting substructure search for job " + jobNo
				+ " on target " + target);
		return super.submitItem(item);
	}

	/**
	 * Indicates that all requests for a given job have been submitted.
	 * 
	 * Blocks until all searches are completed.
	 * 
	 * @param jobNo
	 * @return number of hits for the job
	 */
	public int finishSearch(int jobNo) {
		// make sure to get the job info before calling super.finishJob()
		SubSearchTaskJobInfo taskJobInfo = (SubSearchTaskJobInfo) getTaskJobInfo(jobNo);
		// now wait for all searches to finish.
		super.finishJob(jobNo);
		// all searches finished - clean up
		int nHits = taskJobInfo.nHits.get();
		taskJobInfo.taskJobResults.finish();
		return nHits;
	}

	/*
	 * Takes an item off the search queue and matches it against the job query.
	 * 
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.cairn.rmi.index.TaskPool#processItem(com.cairn.rmi.index
	 * .TaskPool.TaskJobInfo, com.cairn.rmi.index.TaskPool.QueueItem)
	 */
	@Override
	public void processItem(TaskJobInfo jobInfo, QueueItem queueItem) {

		SubSearchQueueItem item = (SubSearchQueueItem) queueItem;
		int jobNo = item.getJobNo();

		SubSearchTaskJobInfo taskJobInfo = (SubSearchTaskJobInfo) jobInfo;
		SubstructureMatcher matcher = taskJobInfo.matcher;

		// use cache if available
		String target = item.target;
		boolean match = matcher.matchStructure(target, item.trusted, item.targetFingerprint);

		if (match) {
			logger.debug("For job no " + jobNo + " target " + target
					+ " is a hit");

			synchronized (taskJobInfo.getLock()) {
				// add the hit if we haven't obtained maximum number of hits
				if (!taskJobInfo.maxHitsObtained()) {
					taskJobInfo.taskJobResults.addHit(item.rowKey.getRowId(),null);
					taskJobInfo.nHits.incrementAndGet();
				} else {
					// max hits obtained, request stop
					taskJobInfo.setStopSubmission();
				}
			}
		}

		logger.trace("Finished matching job no " + jobNo + " target " + target);
	}

	/**
	 * @return the nThreads
	 */
	public static int getnThreads() {
		return nThreads;
	}

	/**
	 * @return the useSubstructureSearchPool
	 */
	public static boolean isUseSubstructureSearchPool() {
		return useSubstructureSearchPool;
	}

	/**
	 * @param nThreads
	 *            the nThreads to set
	 */
	public static void setnThreads(int nThreads) {
		SubstructureSearchPool.nThreads = nThreads;
	}

	/**
	 * @param useSubstructureSearchPool
	 *            the useSubstructureSearchPool to set
	 */
	public static void setUseSubstructureSearchPool(
			boolean useSubstructureSearchPool) {
		SubstructureSearchPool.useSubstructureSearchPool = useSubstructureSearchPool;
	}

}
