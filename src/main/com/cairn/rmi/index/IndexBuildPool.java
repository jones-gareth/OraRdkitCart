package com.cairn.rmi.index;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

/**
 * A thread pool to distribute index building.
 * 
 * @author gjones
 * 
 */
public class IndexBuildPool extends TaskPool {
	private static volatile int nThreads = 1;
	private static volatile boolean useIndexBuildPool = false;

	private final AtomicInteger jobNoCounter = new AtomicInteger(1);
	private static final Logger logger = Logger.getLogger(IndexBuildPool.class);

	private IndexBuildPool() {
		super("indexBuildPool", nThreads);
	}

	private static class IndexBuildItem extends QueueItem {
		final RowKey rowKey;
		final Object rowValue;

		IndexBuildItem(int jobNo, RowKey rowKey, Object rowValue) {
			super(jobNo);
			this.rowKey = rowKey;
			this.rowValue = rowValue;
		}

		/**
		 * @return the rowKey
		 */
        RowKey getRowKey() {
			return rowKey;
		}

		/**
		 * @return the rowValue
		 */
        Object getRowValue() {
			return rowValue;
		}

	}

	private static class IndexBuildTaskJobInfo extends TaskJobInfo {
		private final TableIndex tableIndex;

		IndexBuildTaskJobInfo(int jobNo, TableIndex tableIndex) {
			super(jobNo);
			this.tableIndex = tableIndex;
		}

		/**
		 * @return the tableIndex
		 */
        TableIndex getTableIndex() {
			return tableIndex;
		}

	}

	private static volatile IndexBuildPool indexBuildPool = null;

	public static synchronized IndexBuildPool getInstance() {
		if (indexBuildPool == null) {
			indexBuildPool = new IndexBuildPool();
			indexBuildPool.start();
		}
		return indexBuildPool;
	}

	@Override
	public void processItem(TaskJobInfo taskJobInfo, QueueItem item) {

		// add an entry to the index.
		IndexBuildTaskJobInfo buildInfo = (IndexBuildTaskJobInfo) taskJobInfo;
		IndexBuildItem buildItem = (IndexBuildItem) item;

		buildInfo.getTableIndex().createEntry(buildItem.getRowKey(),
				buildItem.getRowValue());

		int nAdded = buildInfo.getnItemsFinished();
		if (nAdded % 30000 == 0)
			logger.info("Added " + nAdded + " items to index");

	}

	/**
	 * Submit an new row to add to the index.
	 * 
	 * @param jobNo
	 * @param rowKey
	 * @param rowValue
	 */
	public void submitRow(int jobNo, RowKey rowKey, Object rowValue) {
		IndexBuildItem buildItem = new IndexBuildItem(jobNo, rowKey, rowValue);
		submitItem(buildItem);
	}

	/**
	 * Set up building an index
	 * 
	 * @param tableIndex
	 * @return the job no
	 */
	public int startJob(TableIndex tableIndex) {
		int jobNo = jobNoCounter.incrementAndGet();
		IndexBuildTaskJobInfo buildInfo = new IndexBuildTaskJobInfo(jobNo, tableIndex);
		startJob(buildInfo);
		return jobNo;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.cairn.rmi.index.TaskPool#finishJob(int)
	 */
	@Override
	public void finishJob(int jobNo) {
		IndexBuildTaskJobInfo buildInfo = (IndexBuildTaskJobInfo) getTaskJobInfo(jobNo);
		super.finishJob(jobNo);
		int nAdded = buildInfo.getnItemsFinished();
		logger.info("Finished building index: added " + nAdded + " items to index");
	}

	/**
	 * @return the nThreads
	 */
	public static int getnThreads() {
		return nThreads;
	}

	/**
	 * @return the useIndexBuildPool
	 */
	public static boolean isUseIndexBuildPool() {
		return useIndexBuildPool;
	}

	/**
	 * @param nThreads
	 *            the nThreads to set
	 */
	public static void setnThreads(int nThreads) {
		IndexBuildPool.nThreads = nThreads;
	}

	/**
	 * @param useIndexBuildPool
	 *            the useIndexBuildPool to set
	 */
	public static void setUseIndexBuildPool(boolean useIndexBuildPool) {
		IndexBuildPool.useIndexBuildPool = useIndexBuildPool;
	}

}
