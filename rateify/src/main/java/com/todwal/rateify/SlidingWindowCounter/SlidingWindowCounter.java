package com.todwal.rateify.SlidingWindowCounter;

import com.todwal.rateify.Constants.Algorithm;
import com.todwal.rateify.DTO.RateLimiterDTO;
import com.todwal.rateify.RateLimiter;
import com.todwal.rateify.RateLimiterPolicy;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

@Service
public class SlidingWindowCounter implements RateLimiter {

    private final ConcurrentHashMap<String, SlidingWindowCounterParams> windows = new ConcurrentHashMap<>();

    @Override
    public Algorithm getAlgorithm() { return Algorithm.SLIDING_WINDOW_COUNTER; }

    @Override
    public RateLimiterDTO tryAcquire(String key, RateLimiterPolicy policy) {
        SlidingWindowCounterParams params = windows.computeIfAbsent(key,
                k -> new SlidingWindowCounterParams(policy.getLimit(), policy.getWindowSizeMs() * 1_000_000L));

        synchronized (params) {
            long nowNanos = System.nanoTime();

            // Advance the fixed window forward in time
            long windowsElapsed = (nowNanos - params.getWindowStartNanos()) / params.getWindowSizeNs();
            if (windowsElapsed >= 1) {
                // If 2+ full windows passed, all previous data is stale
                params.setPrevCount(windowsElapsed >= 2 ? 0 : params.getCurrCount());
                params.setCurrCount(0);
                params.setWindowStartNanos(params.getWindowStartNanos() + windowsElapsed * params.getWindowSizeNs());
            }

            long windowEndNanos = params.getWindowStartNanos() + params.getWindowSizeNs();

            // Weighted estimate: prevCount contributes proportionally to how much of the
            // previous window overlaps the current sliding window position
            double overlap = (double) (windowEndNanos - nowNanos) / params.getWindowSizeNs();
            double estimate = params.getPrevCount() * overlap + params.getCurrCount();

            boolean allowed = estimate < params.getLimit();

            if (allowed) {
                params.setCurrCount(params.getCurrCount() + 1);
                long remaining = Math.max(0, params.getLimit() - (long) Math.ceil(estimate) - 1);
                return RateLimiterDTO.builder()
                        .allowed(true)
                        .remainingToken(remaining)
                        .retryAfterMs(0L)
                        .resetAtEpochMs(System.currentTimeMillis() + (windowEndNanos - nowNanos) / 1_000_000L)
                        .reason("ok")
                        .build();
            }

            // Solve for when the weighted estimate drops below limit as time advances:
            // prevCount * (windowEnd - t) / windowSizeNs + currCount < limit
            // => t > windowEnd - windowSizeNs * (limit - currCount) / prevCount
            long retryAfterNs;
            if (params.getPrevCount() > 0) {
                double requiredOverlap = (double) (params.getLimit() - params.getCurrCount()) / params.getPrevCount();
                long targetNanos = windowEndNanos - (long) (params.getWindowSizeNs() * requiredOverlap);
                retryAfterNs = Math.max(1_000_000L, targetNanos - nowNanos);
            } else {
                retryAfterNs = windowEndNanos - nowNanos;
            }

            long retryAfterMs = retryAfterNs / 1_000_000L;
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
