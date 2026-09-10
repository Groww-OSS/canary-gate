package io.github.nilavanraj.canarygate.core;

import io.github.nilavanraj.canarygate.model.StepOutcome;

// basic sliding window for failer rate
public class StepHealthRecord {

    private final boolean[] isFailure;
    private final boolean[] filled;
    private final int windowSize;

    private int cursor    = 0;
    private int count     = 0;
    private int failures  = 0;

    public StepHealthRecord(int windowSize) {
        this.windowSize = Math.max(1, windowSize);
        this.isFailure  = new boolean[this.windowSize];
        this.filled     = new boolean[this.windowSize];
    }

    public synchronized void record(StepOutcome outcome) {
        boolean newIsFailure = (outcome == StepOutcome.FAILURE);

        if (filled[cursor]) {
            if (isFailure[cursor]) failures--;
        } else {
            filled[cursor] = true;
            count++;
        }

        isFailure[cursor] = newIsFailure;
        if (newIsFailure) failures++;

        cursor = (cursor + 1) % windowSize;
    }

    public synchronized double failureRate() {
        return count == 0 ? 0.0 : (double) failures / count;
    }

    public synchronized int getTotal() {
        return count;
    }

}
