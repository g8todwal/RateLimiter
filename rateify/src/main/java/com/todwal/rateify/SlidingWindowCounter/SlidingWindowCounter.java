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
                k -> new SlidingWindowCounterParams(policy.getLimit(), policy.getWindowSizeMs()));

        synchronized (params) {
            long now = System.currentTimeMillis();

            // Advance the fixed window if time has moved forward
            long elapsed = now - params.getWindowStart();
            long windowsElapsed = elapsed / params.getWindowSizeMs();
            if (windowsElapsed >= 1) {
                // If 2+ full windows passed, previous data is entirely stale
                params.setPrevCount(windowsElapsed >= 2 ? 0 : params.getCurrCount());
                params.setCurrCount(0);
                params.setWindowStart(params.getWindowStart() + windowsElapsed * params.getWindowSizeMs());
            }

            long windowEnd = params.getWindowStart() + params.getWindowSizeMs();

            // Weighted estimate: prevCount contributes proportionally to how much of the
            // previous window overlaps the current sliding window
            double overlap = (double) (windowEnd - now) / params.getWindowSizeMs();
            double estimate = params.getPrevCount() * overlap + params.getCurrCount();

            boolean allowed = estimate < params.getLimit();

            if (allowed) {
                params.setCurrCount(params.getCurrCount() + 1);
                long remaining = Math.max(0, params.getLimit() - (long) Math.ceil(estimate) - 1);
                return RateLimiterDTO.builder()
                        .allowed(true)
                        .remainingToken(remaining)
                        .retryAfterMs(0L)
                        .resetAtEpochMs(windowEnd)
                        .reason("ok")
                        .build();
            }

            // Solve for when estimate drops below limit as time advances:
            // prevCount * (windowEnd - t) / windowSizeMs + currCount < limit
            // => t > windowEnd - windowSizeMs * (limit - currCount) / prevCount
            long retryAfterMs;
            if (params.getPrevCount() > 0) {
                double requiredOverlap = (double) (params.getLimit() - params.getCurrCount()) / params.getPrevCount();
                long targetTime = windowEnd - (long) (params.getWindowSizeMs() * requiredOverlap);
                retryAfterMs = Math.max(1L, targetTime - now);
            } else {
                retryAfterMs = windowEnd - now;
            }

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
