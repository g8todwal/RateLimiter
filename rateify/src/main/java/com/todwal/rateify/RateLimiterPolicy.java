package com.todwal.rateify;

import com.todwal.rateify.Constants.Algorithm;

public class RateLimiterPolicy {
    private final Algorithm algorithm;
    private final long bucketCapacity;
    private final double refillRate;
    private final long windowSizeMs;
    private final long limit;

    private RateLimiterPolicy(Builder builder) {
        this.algorithm = builder.algorithm;
        this.bucketCapacity = builder.bucketCapacity;
        this.refillRate = builder.refillRate;
        this.windowSizeMs = builder.windowSizeMs;
        this.limit = builder.limit;
    }

    public Algorithm getAlgorithm() { return algorithm; }
    public long getBucketCapacity() { return bucketCapacity; }
    public double getRefillRate() { return refillRate; }
    public long getWindowSizeMs() { return windowSizeMs; }
    public long getLimit() { return limit; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Algorithm algorithm;
        private long bucketCapacity;
        private double refillRate;
        private long windowSizeMs;
        private long limit;

        public Builder algorithm(Algorithm algorithm) { this.algorithm = algorithm; return this; }
        public Builder bucketCapacity(long bucketCapacity) { this.bucketCapacity = bucketCapacity; return this; }
        public Builder refillRate(double refillRate) { this.refillRate = refillRate; return this; }
        public Builder windowSizeMs(long windowSizeMs) { this.windowSizeMs = windowSizeMs; return this; }
        public Builder limit(long limit) { this.limit = limit; return this; }
        public RateLimiterPolicy build() { return new RateLimiterPolicy(this); }
    }
}
