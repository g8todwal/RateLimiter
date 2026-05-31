package com.todwal.rateify.SlidingWindowLog;

import java.util.ArrayDeque;
import java.util.Deque;

public class SlidingWindowLogParams {
    private final long limit;
    private final long windowSizeMs;
    private final Deque<Long> timestamps;

    public SlidingWindowLogParams(long limit, long windowSizeMs) {
        this.limit = limit;
        this.windowSizeMs = windowSizeMs;
        this.timestamps = new ArrayDeque<>();
    }

    public long getLimit() { return limit; }
    public long getWindowSizeMs() { return windowSizeMs; }
    public Deque<Long> getTimestamps() { return timestamps; }
}
