package com.cairn.rmi.server;

import java.util.concurrent.Executor;

import org.apache.log4j.Logger;

import com.cairn.rmi.TaskException;
import com.cairn.rmi.common.HitListChunk;

/**
 * A class to facilitate submitting jobs to the batch system.
 *
 * @author gjones
 */
public abstract class TaskJob {
    private final int jobNo;
    private final Logger logger = Logger.getLogger(TaskJob.class);

    public TaskJob() {
        super();
        jobNo = BatchSystem.createBatchQueue();
    }

    /**
     * Extend this to run your job.
     *
     * @throws TaskException
     */
    protected abstract void runSearch() throws TaskException;

    /**
     * Runs the job on the batch job executor and waits for the first chunk of
     * results.
     *
     * @return
     */
    public HitListChunk runJob() {
        Executor executor = BatchSystem.getBatchJobExecutor();
        logger.debug("Submitting batch search job " + getClass().getName()
                + " on executor " + executor);
        executor.execute(() -> {
            try {
                runSearch();
            } catch (Throwable exception) {
                String message = "Exception running search";
                // make sure that there is something put on the queue !
                HitListChunk chunk = new HitListChunk(jobNo, true, null,
                        null);
                // add error to chunk so that consumer can see it
                chunk.setException(exception);
                BatchSystem.putResults(jobNo, chunk);
                logger.error(message, exception);
                if (exception instanceof Error)
                    throw (Error) exception;
                if (exception instanceof RuntimeException)
                    throw (RuntimeException) exception;
                else
                    throw new RuntimeException(message, exception);
            }
        });

        return BatchSystem.takeResults(jobNo);
    }

    /**
     * @return the jobNo
     */
    protected int getJobNo() {
        return jobNo;
    }

}
