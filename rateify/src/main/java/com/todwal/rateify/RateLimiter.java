package com.todwal.rateify;

import com.todwal.rateify.DTO.RateLimiterDTO;

public interface RateLimiter {
    RateLimiterDTO tryAcquire(String key, RateLimiterPolicy policy);
}
