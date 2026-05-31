package com.todwal.rateify.TokenBucket;

import com.todwal.rateify.Constants.Algorithm;
import com.todwal.rateify.DTO.RateLimiterDTO;
import com.todwal.rateify.RateLimiterPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TokenBucketTest {

    private TokenBucket bucket;
    private RateLimiterPolicy policy;

    @BeforeEach
    void setUp() {
        bucket = new TokenBucket();
        policy = RateLimiterPolicy.builder()
                .algorithm(Algorithm.TOKEN_BUCKET)
                .bucketCapacity(5)
                .refillRate(1.0)   // 1 token/sec — no refill within a sub-second test
                .build();
    }

    @Test
    void burst_drainsToCapacityThenDenies() {
        for (int i = 0; i < 5; i++) {
            assertThat(bucket.tryAcquire("k", policy).isAllowed())
                    .as("call %d should be allowed", i + 1).isTrue();
        }
        RateLimiterDTO denied = bucket.tryAcquire("k", policy);
        assertThat(denied.isAllowed()).isFalse();
        assertThat(denied.getReason()).isEqualTo("bucket_empty");
    }

    @Test
    void steadyState_remainingDecrementsEachCall() {
        long prev = policy.getBucketCapacity();
        for (int i = 0; i < 5; i++) {
            RateLimiterDTO result = bucket.tryAcquire("k", policy);
            assertThat(result.getRemainingToken()).isLessThan(prev);
            prev = result.getRemainingToken();
        }
        assertThat(prev).isZero();
    }

    @Test
    void deniedResponse_hasPositiveRetryAfter() {
        for (int i = 0; i < 5; i++) bucket.tryAcquire("k", policy);
        RateLimiterDTO denied = bucket.tryAcquire("k", policy);
        assertThat(denied.getRetryAfterMs()).isPositive();
        assertThat(denied.getResetAtEpochMs()).isGreaterThan(System.currentTimeMillis());
    }

    @Test
    void keyIsolation_differentKeyHasFullBucket() {
        for (int i = 0; i < 5; i++) bucket.tryAcquire("key-a", policy);
        assertThat(bucket.tryAcquire("key-a", policy).isAllowed()).isFalse();
        assertThat(bucket.tryAcquire("key-b", policy).isAllowed()).isTrue();
    }

    @Test
    void timeBoundary_refillAfterOneSec() throws InterruptedException {
        for (int i = 0; i < 5; i++) bucket.tryAcquire("k", policy);
        assertThat(bucket.tryAcquire("k", policy).isAllowed()).isFalse();

        Thread.sleep(1100);   // wait for 1 token to refill at 1 token/sec

        assertThat(bucket.tryAcquire("k", policy).isAllowed()).isTrue();
        assertThat(bucket.tryAcquire("k", policy).isAllowed()).isFalse();
    }

    @Test
    void concurrentAccess_neverExceedsCapacity() throws InterruptedException {
        int threads = 20;
        AtomicInteger allowed = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                ready.countDown();
                try {
                    start.await();
                    if (bucket.tryAcquire("concurrent", policy).isAllowed()) allowed.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }).start();
        }

        ready.await();        // all threads staged before firing
        start.countDown();
        done.await(5, TimeUnit.SECONDS);

        // refillRate=1/sec so no refill happens in the < 1s test window
        assertThat(allowed.get()).isLessThanOrEqualTo((int) policy.getBucketCapacity());
    }
}
