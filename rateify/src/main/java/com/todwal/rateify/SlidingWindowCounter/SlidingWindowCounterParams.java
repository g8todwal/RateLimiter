package com.todwal.rateify.SlidingWindowCounter;

public class SlidingWindowCounterParams {
    private final long limit;
    private final long windowSizeNs;
    private long windowStartNanos;
    private long prevCount;
    private long currCount;

    public SlidingWindowCounterParams(long limit, long windowSizeNs) {
        this.limit = limit;
        this.windowSizeNs = windowSizeNs;
        this.windowStartNanos = System.nanoTime();
        this.prevCount = 0;
        this.currCount = 0;
    }

    public long getLimit() { return limit; }
    public long getWindowSizeNs() { return windowSizeNs; }
    public long getWindowStartNanos() { return windowStartNanos; }
    public void setWindowStartNanos(long windowStartNanos) { this.windowStartNanos = windowStartNanos; }
    public long getPrevCount() { return prevCount; }
    public void setPrevCount(long prevCount) { this.prevCount = prevCount; }
    public long getCurrCount() { return currCount; }
    public void setCurrCount(long currCount) { this.currCount = currCount; }
}
