package com.cairn.rmi.server;

import com.cairn.rmi.common.HitListChunk;
import org.apache.commons.collections.CollectionUtils;

import java.util.ArrayList;

/**
 * A class to facilitate putting results in chunks on the job queue.
 *
 * @author Gareth Jones
 */
public class TaskJobResults {
    private final int jobNo;
    private final ArrayList<byte[]> hits = new ArrayList<>();
    private final ArrayList<Double> scoreList = new ArrayList<>();
    private volatile boolean start = true, finished = false;
    // The first chunk size can be different so as to return initial results to
    // the user as soon as possible.
    private static final int FIRST_CHUNK_SIZE = 100, CHUNK_SIZE = 2000;
    private final boolean hasScores;

    /**
     * @param jobNo
     * @param hasScores
     */
    public TaskJobResults(int jobNo, boolean hasScores) {
        super();
        this.jobNo = jobNo;
        this.hasScores = hasScores;
    }

    /**
     * Posts a hit from a search
     *
     * @param hit
     * @param score
     */
    public synchronized void addHit(byte[] hit, Double score) {
        if (finished)
            throw new IllegalStateException("The search is finished");
        hits.add(hit);

        if (hasScores && score == null)
            throw new IllegalStateException("No score present!");
        if (!hasScores && score != null)
            throw new IllegalStateException("Score present!");
        if (hasScores)
            scoreList.add(score);

        // put new chunks on the job queue if necessary
        if (start && hits.size() == FIRST_CHUNK_SIZE) {
            putJobResultsOnQueue(false);
            start = false;
        } else if (hits.size() == CHUNK_SIZE) {
            putJobResultsOnQueue(false);
        }

    }

    /**
     * Finish the search
     */
    public synchronized void finish() {
        finished = true;
        putJobResultsOnQueue(true);
    }

    /**
     * Create and post a new chunk to the job queue.
     *
     * @param finished
     */
    private void putJobResultsOnQueue(boolean finished) {
        String[] hitlist = null;
        if (CollectionUtils.isNotEmpty(hits)) {
            hitlist = hits.stream().map(String::new).toArray(String[]::new);
            hits.clear();
        }
        double[] scores = null;
        if (hasScores && CollectionUtils.isNotEmpty(scoreList)) {
            scores = scoreList.stream().mapToDouble(d -> d).toArray();
            scoreList.clear();
        }
        HitListChunk chunk = new HitListChunk(jobNo, finished, hitlist, scores);
        BatchSystem.putResults(jobNo, chunk);
    }

    /**
     * @return the jobNo
     */
    public int getJobNo() {
        return jobNo;
    }

    /**
     * @return the finished
     */
    public boolean isFinished() {
        return finished;
    }

}