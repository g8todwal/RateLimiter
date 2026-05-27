package com.todwal.rateify.DTO;

public class RateLimiterDTO {
    public boolean allowed;
    public long remainingToken;
    public long retryAfterMs;
    public long resetAtEpochMs;
    public String reason;

    private RateLimiterDTO(Builder builder){
        this.allowed = builder.allowed;
        this.resetAtEpochMs = builder.resetAtEpochMs;
        this.retryAfterMs = builder.retryAfterMs;
        this.remainingToken = builder.remainingToken;
        this.reason = builder.reason;
    }
    public static Builder builder() { return new Builder(); }

    public static class Builder{
        private boolean allowed;
        private long remainingToken;
        private long retryAfterMs;
        private long resetAtEpochMs;
        private String reason;

        public Builder allowed(boolean allowed){ this.allowed = allowed; return this;}
        public Builder remainingToken(long remainingToken){this.remainingToken = remainingToken; return this;}
        public Builder resetAtEpochMs(long resetAtEpochMs) { this.resetAtEpochMs = resetAtEpochMs; return this; }
        public Builder retryAfterMs(long retryAfterMs) { this.retryAfterMs = retryAfterMs; return this; }
        public Builder reason(String reason) { this.reason = reason; return this; }
        public RateLimiterDTO build() { return new RateLimiterDTO(this); }
    }
}
