package com.todwal.rateify.TokenBucket;

import com.todwal.rateify.DTO.RateLimiterDTO;
import com.todwal.rateify.RateLimiter;
import com.todwal.rateify.RateLimiterPolicy;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

@Service
public class TokenBucket implements RateLimiter {

    private final ConcurrentHashMap<String, TokenBucketParams> buckets = new ConcurrentHashMap<>();

    @Override
    public RateLimiterDTO tryAcquire(String key, RateLimiterPolicy policy) {
        TokenBucketParams params = buckets.computeIfAbsent(key,
                k -> new TokenBucketParams(policy.getBucketCapacity(), policy.getRefillRate()));

        synchronized (params) {
            double currentTime = System.currentTimeMillis();
            double elapsedTime = (currentTime - params.getLastRefillTime()) / 1000.0;
            double refillToken = elapsedTime * params.getRefillRate();

            long currentToken = Math.min(params.getBucketCapacity(),
                    params.getToken() + (long) refillToken);

            params.setLastRefillTime((long) currentTime);

            boolean allowed = false;
            if (currentToken >= 1) {
                currentToken -= 1;
                params.setToken(currentToken);
                allowed = true;
            }

            long retryAfterMs = allowed ? 0L : (long) (1000.0 / params.getRefillRate());
            return RateLimiterDTO.builder()
                    .allowed(allowed)
                    .remainingToken(currentToken)
                    .retryAfterMs(retryAfterMs)
                    .resetAtEpochMs((long) currentTime + retryAfterMs)
                    .reason(allowed ? "ok" : "bucket_empty")
                    .build();
        }
    }
}
