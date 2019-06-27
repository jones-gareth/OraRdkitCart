package com.cairn.rmi.server;

import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.Logger;

import com.cairn.rmi.common.HitListChunk;

/**
 * Static methods for managing batch queues of results.
 * <p>
 * There are three executor services: one to run tasks on. The second to run
 * batch jobs (those that return hitlists) and a third to retrieve batch jobs
 * results.
 * <p>
 * The batch jobs return data in chunks of hitlists. For substructure (or
 * similar search) a user will submit a job to the task executor, the task will
 * submit a job on the batch queue which will write results in chunks on a batch
 * queue. The task that submitted the search will take the first set of results
 * and return to the client. The client can get later results using a
 * HitListBatchTask request which is run through the hitListExecutor. The use of
 * the three thread groups ensures that there will never be deadlock yet
 * prevents too many threads being run.
 * <p>
 * Thread safe.
 *
 * @author gjones
 */
public final class BatchSystem {
    private static final Logger logger = Logger.getLogger(BatchSystem.class);

    private static final AtomicInteger jobNo = new AtomicInteger();

    private static final Map<Integer, JobQueue> batchQueues = new ConcurrentHashMap<>();

    /**
     * Executor for Tasks
     */
    private static volatile ExecutorService taskExecutor;

    /**
     * This executor will return hit lists to the rmi client. These tasks will
     * be fed from threads fired by tasks in the executor service. We use a
     * separate thread pool for these guys to ensure that we avoid deadlocks.
     */
    private static volatile ExecutorService hitListExecutor;

    /**
     * This executor is used to run batch jobs.
     */
    private static volatile ExecutorService batchJobExecutor;

    /**
     * This executor removes batch queues that still have data on them after
     * REAP_TIME_MINUTES
     */
    private static volatile ScheduledExecutorService queueReaper;
    private static final long REAP_TIME_MINUTES = 60;

    private BatchSystem() {
    }

    /**
     * shutdowns all executors.
     */
    public static synchronized void stop() {
        if (taskExecutor != null)
            taskExecutor.shutdown();
        if (hitListExecutor != null)
            hitListExecutor.shutdown();
        if (batchJobExecutor != null)
            batchJobExecutor.shutdown();
        if (queueReaper != null)
            queueReaper.shutdown();
    }

    /**
     * starts (creates) all executors.
     *
     * @param nThreads number of batch and task threads
     */
    public static synchronized void start(int nThreads) {
        taskExecutor = Executors.newFixedThreadPool(nThreads,
                new BatchThreadFactory("taskThread"));
        hitListExecutor = Executors.newFixedThreadPool(nThreads * 2,
                new BatchThreadFactory("hitListThread"));
        batchJobExecutor = Executors.newFixedThreadPool(nThreads,
                new BatchThreadFactory("batchJobThread"));

        // create a cleanup reaper to remove job results that have not been
        // processed after an hour.
        queueReaper = Executors
                .newSingleThreadScheduledExecutor(new BatchThreadFactory(
                        "queueReaperThread"));
        Runnable command = () -> {
            long cutoff = REAP_TIME_MINUTES * 60 * 1000;
            long currentTime = System.currentTimeMillis();
            for (Entry<Integer, JobQueue> entry : batchQueues.entrySet()) {
                long time = currentTime - entry.getValue().getTimeCreated();
                if (time > cutoff) {
                    int jobNo = entry.getKey();
                    batchQueues.remove(jobNo);
                    logger.info("Job queue entry " + jobNo
                            + " cleared after " + REAP_TIME_MINUTES
                            + " minutes");
                }

            }
        };
        queueReaper.scheduleAtFixedRate(command, 0, 10, TimeUnit.MINUTES);

        // create shutdown hooks to make sure that all executors close if the
        // JVM shutdowns.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (!taskExecutor.isTerminated())
                    taskExecutor.shutdownNow();
                if (!hitListExecutor.isTerminated())
                    hitListExecutor.shutdownNow();
                if (!batchJobExecutor.isTerminated())
                    batchJobExecutor.shutdownNow();
                if (!queueReaper.isTerminated())
                    queueReaper.shutdownNow();
        }));
    }

    /**
     * Creates a new batch job queue.
     *
     * @return
     */
    public static int createBatchQueue() {
        int no = jobNo.incrementAndGet();
        batchQueues.put(no, new JobQueue());
        return no;
    }

    /**
     * Takes the next set of results of the a queue. Note that there should
     * never be simultaneous access to the same queue. Removes the queue if this
     * is the last batch.
     *
     * @param jobNo
     * @return
     */
    public static HitListChunk takeResults(int jobNo) {
        HitListChunk batch = null;
        BlockingQueue<HitListChunk> queue = batchQueues.get(jobNo).getQueue();
        if (queue == null) {
            logger.warn("Taking results from missing queue.  Queue has been deleted by reaper?");
            batch = new HitListChunk(jobNo, true, null, null);
            return batch;
        }
        try {
            batch = queue.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (batch.isFinished())
            batchQueues.remove(jobNo);
        return batch;
    }

    /**
     * Adds a set of results onto a queue. Note that there should never be
     * simultaneous access to the same queue.
     *
     * @param jobNo
     * @param batch
     */
    public static void putResults(int jobNo, HitListChunk batch) {
        BlockingQueue<HitListChunk> queue = batchQueues.get(jobNo).getQueue();
        if (queue == null) {
            logger.warn("Putting results on missing queue.  Queue has been deleted by reaper?");
            return;
        }
        try {
            queue.put(batch);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * @return the taskExecutor
     */
    public static ExecutorService getTaskExecutor() {
        return taskExecutor;
    }

    /**
     * @return the hitListExecutor
     */
    public static ExecutorService getHitListExecutor() {
        return hitListExecutor;
    }

    /**
     * @return the batchJobExecutor
     */
    public static ExecutorService getBatchJobExecutor() {
        return batchJobExecutor;
    }

    /**
     * A class to hold the result queue together with date created.
     *
     * @author gjones
     */
    static class JobQueue {
        private final BlockingQueue<HitListChunk> queue = new ArrayBlockingQueue<>(
                200);
        private final long timeCreated = System.currentTimeMillis();

        /**
         * @return the queue
         */
        BlockingQueue<HitListChunk> getQueue() {
            return queue;
        }

        /**
         * @return the timeCreated
         */
        long getTimeCreated() {
            return timeCreated;
        }

    }

}
