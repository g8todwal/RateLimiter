package com.todwal.rateify.SlidingWindowLog;

import com.todwal.rateify.Constants.Algorithm;
import com.todwal.rateify.DTO.RateLimiterDTO;
import com.todwal.rateify.RateLimiter;
import com.todwal.rateify.RateLimiterPolicy;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

@Service
public class SlidingWindowLog implements RateLimiter {

    private final ConcurrentHashMap<String, SlidingWindowLogParams> windows = new ConcurrentHashMap<>();

    @Override
    public Algorithm getAlgorithm() { return Algorithm.SLIDING_WINDOW_LOG; }

    @Override
    public RateLimiterDTO tryAcquire(String key, RateLimiterPolicy policy) {
        SlidingWindowLogParams params = windows.computeIfAbsent(key,
                k -> new SlidingWindowLogParams(policy.getLimit(), policy.getWindowSizeMs() * 1_000_000L));

        synchronized (params) {
            long nowNanos = System.nanoTime();
            long windowStartNanos = nowNanos - params.getWindowSizeNs();

            // Evict timestamps that have fallen outside the sliding window
            while (!params.getTimestamps().isEmpty() && params.getTimestamps().peekFirst() <= windowStartNanos) {
                params.getTimestamps().pollFirst();
            }

            long count = params.getTimestamps().size();
            boolean allowed = count < params.getLimit();

            if (allowed) {
                params.getTimestamps().addLast(nowNanos);
                return RateLimiterDTO.builder()
                        .allowed(true)
                        .remainingToken(params.getLimit() - count - 1)
                        .retryAfterMs(0L)
                        .resetAtEpochMs(System.currentTimeMillis() + params.getWindowSizeNs() / 1_000_000L)
                        .reason("ok")
                        .build();
            }

            // Retry after the oldest request slides out of the window
            long retryAfterNs = (params.getTimestamps().peekFirst() + params.getWindowSizeNs()) - nowNanos;
            long retryAfterMs = Math.max(1L, retryAfterNs / 1_000_000L);
            return RateLimiterDTO.builder()
                    .allowed(false)
                    .remainingToken(0L)
                    .retryAfterMs(retryAfterMs)
                    .resetAtEpochMs(System.currentTimeMillis() + retryAfterMs)
                    .reason("window_full")
                    .build();
        }
    }
}
