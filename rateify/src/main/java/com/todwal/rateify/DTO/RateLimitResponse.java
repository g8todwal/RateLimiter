package com.todwal.rateify.DTO;

public record RateLimitResponse(boolean allowed, long remaining, long resetAt) {}
