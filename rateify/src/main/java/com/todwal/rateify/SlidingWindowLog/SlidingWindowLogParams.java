package com.todwal.rateify.SlidingWindowLog;

import java.util.ArrayDeque;
import java.util.Deque;

public class SlidingWindowLogParams {
    private final long limit;
    private final long windowSizeNs;
    private final Deque<Long> timestamps;

    public SlidingWindowLogParams(long limit, long windowSizeNs) {
        this.limit = limit;
        this.windowSizeNs = windowSizeNs;
        this.timestamps = new ArrayDeque<>();
    }

    public long getLimit() { return limit; }
    public long getWindowSizeNs() { return windowSizeNs; }
    public Deque<Long> getTimestamps() { return timestamps; }
}
