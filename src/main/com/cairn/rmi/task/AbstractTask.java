package com.cairn.rmi.task;

import com.cairn.rmi.TaskException;
import com.cairn.rmi.TaskInterface;
import com.cairn.rmi.server.BatchSystem;
import org.apache.log4j.Logger;

import java.io.Serializable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Base task. Handles passing settings and results and Task submission (for when
 * a proxy is not used). Task should extend this.
 *
 * @author Gareth Jones
 */
abstract public class AbstractTask implements Serializable, TaskInterface {

    /**
     * Eclipse default serial version id
     */
    private static final long serialVersionUID = 1000L;
    Object settings;
    Object results;
    private static final Logger logger = Logger.getLogger(AbstractTask.class);

    public Object getResult() {
        return results;
    }

    public Object getSettings() {
        return settings;
    }

    public Object submit(Object settings) throws TaskException {
        this.settings = settings;
        this.results = submit();
        return results;
    }

    /**
     * Runs the task and returns the result- essentially a wrapper for
     * submitTask. Contains code for queuing tasks
     *
     * @return
     * @throws TaskException
     */
    private Object submit() throws TaskException {

        ExecutorService executor = getExecutorService();
        logger.debug("Submitting task " + getClass().getName()
                + " on executor " + executor);
        Future<Object> future = executor.submit(() -> {
            submitTask();
            return getResult();
        });

        try {
            Object result = future.get();
            logger.debug("Finished task " + getClass().getName()
                    + " on executor " + executor);
            return result;
        } catch (InterruptedException e) {
            logger.error("Task submission interrupted", e);
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            logger.error("Task threw exception", cause);
            if (cause instanceof TaskException)
                throw (TaskException) cause;
            else if (cause instanceof RuntimeException)
                throw (RuntimeException) cause;
            else if (cause instanceof Error)
                throw (Error) cause;
            else
                throw new IllegalStateException(
                        "Submit failed: unknown exception" + cause);
        }

        // shouldn't ever get here.
        return null;

    }

    ExecutorService getExecutorService() {
        return BatchSystem.getTaskExecutor();
    }

    /**
     * Actually runs the task
     *
     * @return
     * @throws TaskException
     */
    protected abstract Object submitTask() throws TaskException;
}
