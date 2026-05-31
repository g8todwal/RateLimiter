package com.todwal.rateify.TokenBucket;

record TokenBucketState(long tokens, long lastRefillNanos) {}
