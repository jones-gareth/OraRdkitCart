package com.cairn.rmi.index;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.Logger;

import com.cairn.rmi.server.BatchThreadFactory;

/**
 * Uses a thread pool for managing jobs that process tasks on a queue. A fixed
 * thread pool is shared between all jobs. Each job submits items to the pool
 * and each thread in the pool waits to process the items.
 *
 * @author gjones
 */
public abstract class TaskPool {
    private static final Logger logger = Logger.getLogger(TaskPool.class);

    // Thread pool for processing items
    private final ExecutorService taskService;
    private final int nThreads;

    /**
     * Data structure to store individual requests- extend this for your
     * specific task
     */
    static class QueueItem {
        final int jobNo;

        QueueItem(int jobNo) {
            super();
            this.jobNo = jobNo;
        }

        /**
         * @return the jobNo
         */
        int getJobNo() {
            return jobNo;
        }

    }

    // Queue to store pending items
    private final BlockingQueue<QueueItem> itemQueue = new ArrayBlockingQueue<>(1000);

    /**
     * A class to store information about each job- extend for your specific
     * task
     */
    protected static class TaskJobInfo {
        private final int jobNo;
        private final Integer lock;

        // To determine if a job is finished determine that the number of
        // items processed matches the number of items added to the queue.

        // This counts searches submitted by job
        private final AtomicInteger nItemsSubmitted = new AtomicInteger();
        // This counts searches finished by job
        private final AtomicInteger nItemsFinished = new AtomicInteger();

        private final AtomicBoolean stopSubmission = new AtomicBoolean();
        private volatile boolean allItemsSubmitted = false;
        private final Semaphore jobInProgress = new Semaphore(1);
        private volatile Throwable error;

        TaskJobInfo(int jobNo) {
            super();
            this.jobNo = jobNo;
            this.lock = jobNo;
        }

        private boolean isStopSubmission() {
            return stopSubmission.get();
        }

        /**
         * Indicates that we should finish up even if more items are incoming or
         * if there are existing items on the queue.
         */
        void setStopSubmission() {
            stopSubmission.set(true);
        }

        private boolean isFinished() {
            if (isStopSubmission()) {
                logger.trace("isFinished: stop submission set");
                return true;
            }
            if (!allItemsSubmitted) {
                logger.trace("isFinished: not all items submitted returning false");
                return false;
            }
            if (logger.isTraceEnabled()) {
                int nSubmitted = nItemsSubmitted.get();
                int nFinished = nItemsFinished.get();
                logger.trace("isFinished: nSubmitted " + nSubmitted + " nFinished "
                        + nFinished + " returning " + (nFinished == nSubmitted));
            }
            return nItemsSubmitted.get() == nItemsFinished.get();
        }

        /**
         * @return the jobNo
         */
        int getJobNo() {
            return jobNo;
        }

        /**
         * @return the nItensSubmitted
         */
        public int getnItemsSubmitted() {
            return nItemsSubmitted.get();
        }

        /**
         * @return the nItemsFinished
         */
        int getnItemsFinished() {
            return nItemsFinished.get();
        }

        public Object getLock() {
            return lock;
        }
    }

    // maps job number to task information
    private final ConcurrentHashMap<Integer, TaskJobInfo> currentJobs = new ConcurrentHashMap<>();

    TaskPool(String poolName, int nThreads) {
        super();
        this.nThreads = nThreads;
        if (nThreads == 1)
            throw new IllegalStateException(
                    "Pointless creation of task thread pool with only one thread");
        taskService = Executors.newFixedThreadPool(nThreads, new BatchThreadFactory(
                poolName));
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
   }

    /**
     * Start the thread pool. Each thread will process request on the queue
     */
    void start() {
        for (int i = 0; i < nThreads; i++) {
            taskService.execute(() -> {
                logger.info("Starting task pool thread");
                while (true) {
                    try {
                        takeItem();
                    } catch (InterruptedException e) {
                        logger.error("Task pool thread interrupted: stopping.. ", e);
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            });
        }
    }

    /**
     * Stops the thread pool
     */
    private void stop() {
        if (!taskService.isShutdown())
            taskService.shutdownNow();
    }

    /**
     * Re-throws a caught Throwable
     *
     * @param exception
     */
    private void rethrowException(Throwable exception) {
        if (exception instanceof Error)
            throw (Error) exception;
        if (exception instanceof RuntimeException)
            throw (RuntimeException) exception;
        else
            throw new RuntimeException("exception while searching target", exception);
    }

    /**
     * Register a new search
     *
     */
    void startJob(TaskJobInfo taskJobInfo) {
        currentJobs.put(taskJobInfo.getJobNo(), taskJobInfo);
        try {
            taskJobInfo.jobInProgress.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(
                    "Thread interrupted acquiring search in progress mutex");
        }
    }

    /**
     * Adds a search request to the queue
     *
     * @return false if the maximum number of hits has been obtained.
     */
    boolean submitItem(QueueItem item) {
        int jobNo = item.getJobNo();
        logger.trace("Submitting item for job " + jobNo);
        TaskJobInfo taskJobInfo = currentJobs.get(jobNo);

        // throw any search error
        if (taskJobInfo.error != null) {
            rethrowException(taskJobInfo.error);
        }

        // return false if we have a stop submission requested
        if (taskJobInfo.isStopSubmission())
            return false;

        // queue the target structure
        taskJobInfo.nItemsSubmitted.incrementAndGet();
        try {
            itemQueue.put(item);
        } catch (InterruptedException e) {
            String message = "Unable to submit item to task: interrupted";
            logger.error(message, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException(message, e);
        }

        return true;
    }

    /**
     * Indicates that all requests for a given job have been submitted.
     * <p>
     * Blocks until all searches are completed.
     *
     * @param jobNo
     * @return number of hits for the job
     */
    void finishJob(int jobNo) {
        TaskJobInfo taskJobInfo = currentJobs.get(jobNo);
        if (taskJobInfo.error != null)
            rethrowException(taskJobInfo.error);
        taskJobInfo.allItemsSubmitted = true;

        // if there are still running searches wait for them to finish
        if (!taskJobInfo.isFinished()) {
            try {
                Semaphore jobInProgress = taskJobInfo.jobInProgress;
                logger.debug("attempting to acquire job in progress mutex: free permits "
                        + jobInProgress.availablePermits());
                jobInProgress.acquire();
                logger.debug("acquired job in progress mutex");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                String message = "Interrupted waiting for searches to finish";
                logger.error(message, e);
                throw new RuntimeException(message, e);
            }
        }

        // the job is finished finished - clean up
        currentJobs.remove(jobNo);
    }

    /**
     * Takes an item off the search queue and matches it against the job query.
     *
     * @throws InterruptedException
     */
    private void takeItem() throws InterruptedException {
        QueueItem item = itemQueue.take();
        int jobNo = item.jobNo;

        TaskJobInfo taskJobInfo = currentJobs.get(item.jobNo);
        if (taskJobInfo == null) {
            logger.debug("Stale job " + jobNo + " in queue");
            return;
        }

        if (taskJobInfo.isFinished()) {
            logger.debug("Job finished");
            return;
        }

        try {
            processItem(taskJobInfo, item);
        } catch (Throwable exception) {
            String message = "Exception processing item";
            logger.error(message, exception);
            taskJobInfo.error = exception;
            // Propagate most serious errors, but allow item processing to continue with all others.
            if (exception instanceof Error)
                throw (Error) exception;
        }

        taskJobInfo.nItemsFinished.incrementAndGet();
        // If the search is finished release semaphore
        if (taskJobInfo.isFinished())
            taskJobInfo.jobInProgress.release();

    }

    /**
     * Get task information for a job.
     *
     * @param jobNo
     * @return
     */
    TaskJobInfo getTaskJobInfo(int jobNo) {
        return currentJobs.get(jobNo);
    }

    /**
     * Implement this to process an item taken off the queue.
     *
     * @param taskJobInfo
     * @param item
     */
    protected abstract void processItem(TaskJobInfo taskJobInfo, QueueItem item)
    ;

}
