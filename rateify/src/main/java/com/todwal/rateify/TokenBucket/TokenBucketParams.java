package com.todwal.rateify.TokenBucket;

public class TokenBucketParams {
    private final long bucketCapacity;
    private final double refillRate;
    private long lastRefillTime;
    private long token;

    public TokenBucketParams(long bucketCapacity, double refillRate) {
        this.bucketCapacity = bucketCapacity;
        this.refillRate = refillRate;
        this.token = bucketCapacity;
        this.lastRefillTime = System.currentTimeMillis();
    }

    public long getBucketCapacity() { return bucketCapacity; }
    public double getRefillRate() { return refillRate; }
    public long getLastRefillTime() { return lastRefillTime; }
    public void setLastRefillTime(long lastRefillTime) { this.lastRefillTime = lastRefillTime; }
    public long getToken() { return token; }
    public void setToken(long token) { this.token = token; }
}
