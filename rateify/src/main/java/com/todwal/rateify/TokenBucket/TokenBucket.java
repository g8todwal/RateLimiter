package com.todwal.rateify.TokenBucket;

import com.todwal.rateify.Constants.Algorithm;
import com.todwal.rateify.DTO.RateLimiterDTO;
import com.todwal.rateify.RateLimiter;
import com.todwal.rateify.RateLimiterPolicy;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class TokenBucket implements RateLimiter {

    private final ConcurrentHashMap<String, AtomicReference<TokenBucketState>> buckets = new ConcurrentHashMap<>();

    @Override
    public Algorithm getAlgorithm() { return Algorithm.TOKEN_BUCKET; }

    @Override
    public RateLimiterDTO tryAcquire(String key, RateLimiterPolicy policy) {
        AtomicReference<TokenBucketState> ref = buckets.computeIfAbsent(key,
                k -> new AtomicReference<>(new TokenBucketState(policy.getBucketCapacity(), System.nanoTime())));

        TokenBucketState current, next;
        boolean allowed;
        long remaining;

        // CAS loop: read → compute → write atomically; retry if another thread won the race
        do {
            current = ref.get();
            long nowNanos = System.nanoTime();
            long refilled = (long)((nowNanos - current.lastRefillNanos()) / 1_000_000_000.0 * policy.getRefillRate());
            long newTokens = Math.min(policy.getBucketCapacity(), current.tokens() + refilled);

            allowed = newTokens >= 1;
            remaining = allowed ? newTokens - 1 : 0;
            next = new TokenBucketState(remaining, nowNanos);
        } while (allowed && !ref.compareAndSet(current, next));

        long retryAfterMs = allowed ? 0L : (long) (1000.0 / policy.getRefillRate());
        return RateLimiterDTO.builder()
                .allowed(allowed)
                .remainingToken(remaining)
                .retryAfterMs(retryAfterMs)
                .resetAtEpochMs(System.currentTimeMillis() + retryAfterMs)
                .reason(allowed ? "ok" : "bucket_empty")
                .build();
    }
}
