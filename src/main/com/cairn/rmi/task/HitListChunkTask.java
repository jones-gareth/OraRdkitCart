package com.cairn.rmi.task;

import com.cairn.rmi.common.HitListChunk;
import com.cairn.rmi.server.BatchSystem;

import java.util.concurrent.ExecutorService;

/**
 * This task gets a chunk of results from a running task. It does not use the
 * normal task executor as it may filled submitting running jobs.
 * 
 * The task takes a integer job number as its settings object and returns a
 * hitlist chunk.
 * 
 * @author Gareth Jones
 *
 */
public class HitListChunkTask extends AbstractTask {
	private static final long serialVersionUID = 1000L;

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.cairn.rmi.task.AbstractTask#getExecutorService()
	 */
	@Override
	protected ExecutorService getExecutorService() {
		return BatchSystem.getHitListExecutor();
	}

	@Override
	public Object submitTask() {
		int jobNo = (Integer) settings;
		HitListChunk hitList = BatchSystem.takeResults(jobNo);
		results = hitList;
		return hitList;
	}

}
