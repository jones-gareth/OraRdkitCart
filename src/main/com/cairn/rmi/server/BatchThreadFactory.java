package com.cairn.rmi.server;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread Factory to create usefully named threads.
 * 
 */
public class BatchThreadFactory implements ThreadFactory {

	private final AtomicInteger counter = new AtomicInteger(0);
	private final String prefix;

	public BatchThreadFactory(String prefix) {
		this.prefix = prefix;
	}

	public Thread newThread(Runnable r) {
		return new Thread(r, prefix + "-" + counter.getAndIncrement());
	}
}