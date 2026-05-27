package com.todwal.rateify;

import com.todwal.rateify.Constants.Algorithm;
import com.todwal.rateify.Constants.Tiers;
import com.todwal.rateify.DTO.RateLimiterDTO;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PolicyRegistry {
    private final Map<Tiers, RateLimiterPolicy> policies;
    private final Map<Algorithm, RateLimiter> rateLimiters;

    public PolicyRegistry(RateLimiter rateLimiter) {
        this.rateLimiters = Map.of(
                Algorithm.TOKEN_BUCKET, rateLimiter
        );

        this.policies = Map.of(
                Tiers.FREE_TIER, RateLimiterPolicy.builder()
                        .algorithm(Algorithm.TOKEN_BUCKET)
                        .bucketCapacity(10)
                        .refillRate(1.0)
                        .build(),
                Tiers.PREMIUM_TIER, RateLimiterPolicy.builder()
                        .algorithm(Algorithm.TOKEN_BUCKET)
                        .bucketCapacity(100)
                        .refillRate(10.0)
                        .build(),
                Tiers.LOGIN_TIER, RateLimiterPolicy.builder()
                        .algorithm(Algorithm.TOKEN_BUCKET)
                        .bucketCapacity(5)
                        .refillRate(0.1)
                        .build()
        );
    }

    public RateLimiterDTO check(String key, Tiers tier) {
        RateLimiterPolicy policy = policies.get(tier);
        if (policy == null) {
            throw new IllegalArgumentException("Unknown tier: " + tier);
        }
        RateLimiter rateLimiter = rateLimiters.get(policy.getAlgorithm());
        return rateLimiter.tryAcquire(key, policy);
    }
}
