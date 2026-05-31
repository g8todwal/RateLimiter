package com.todwal.rateify.SlidingWindowCounter;

public class SlidingWindowCounterParams {
    private final long limit;
    private final long windowSizeMs;
    private long windowStart;
    private long prevCount;
    private long currCount;

    public SlidingWindowCounterParams(long limit, long windowSizeMs) {
        this.limit = limit;
        this.windowSizeMs = windowSizeMs;
        this.windowStart = System.currentTimeMillis();
        this.prevCount = 0;
        this.currCount = 0;
    }

    public long getLimit() { return limit; }
    public long getWindowSizeMs() { return windowSizeMs; }
    public long getWindowStart() { return windowStart; }
    public void setWindowStart(long windowStart) { this.windowStart = windowStart; }
    public long getPrevCount() { return prevCount; }
    public void setPrevCount(long prevCount) { this.prevCount = prevCount; }
    public long getCurrCount() { return currCount; }
    public void setCurrCount(long currCount) { this.currCount = currCount; }
}
