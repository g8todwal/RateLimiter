package com.todwal.rateify;

import com.todwal.rateify.Constants.Algorithm;
import com.todwal.rateify.DTO.RateLimiterDTO;

public interface RateLimiter {
    Algorithm getAlgorithm();
    RateLimiterDTO tryAcquire(String key, RateLimiterPolicy policy);
}
