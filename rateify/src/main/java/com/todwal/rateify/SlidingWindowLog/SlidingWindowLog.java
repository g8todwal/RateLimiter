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
                k -> new SlidingWindowLogParams(policy.getLimit(), policy.getWindowSizeMs()));

        synchronized (params) {
            long now = System.currentTimeMillis();
            long windowStart = now - params.getWindowSizeMs();

            // Evict timestamps that have fallen outside the window
            while (!params.getTimestamps().isEmpty() && params.getTimestamps().peekFirst() <= windowStart) {
                params.getTimestamps().pollFirst();
            }

            long count = params.getTimestamps().size();
            boolean allowed = count < params.getLimit();

            if (allowed) {
                params.getTimestamps().addLast(now);
                return RateLimiterDTO.builder()
                        .allowed(true)
                        .remainingToken(params.getLimit() - count - 1)
                        .retryAfterMs(0L)
                        .resetAtEpochMs(now + params.getWindowSizeMs())
                        .reason("ok")
                        .build();
            }

            // Retry after the oldest request slides out of the window
            long oldestTimestamp = params.getTimestamps().peekFirst();
            long retryAfterMs = (oldestTimestamp + params.getWindowSizeMs()) - now;
            return RateLimiterDTO.builder()
                    .allowed(false)
                    .remainingToken(0L)
                    .retryAfterMs(retryAfterMs)
                    .resetAtEpochMs(now + retryAfterMs)
                    .reason("window_full")
                    .build();
        }
    }
}
