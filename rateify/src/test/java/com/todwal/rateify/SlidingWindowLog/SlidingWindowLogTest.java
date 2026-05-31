package com.todwal.rateify.SlidingWindowLog;

import com.todwal.rateify.Constants.Algorithm;
import com.todwal.rateify.DTO.RateLimiterDTO;
import com.todwal.rateify.RateLimiterPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class SlidingWindowLogTest {

    private SlidingWindowLog limiter;
    private RateLimiterPolicy policy;

    @BeforeEach
    void setUp() {
        limiter = new SlidingWindowLog();
        policy = RateLimiterPolicy.builder()
                .algorithm(Algorithm.SLIDING_WINDOW_LOG)
                .windowSizeMs(100)   // 100 ms window — fast tests, no multi-second sleeps
                .limit(3)
                .build();
    }

    @Test
    void burst_allowsExactlyLimit() {
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryAcquire("k", policy).isAllowed())
                    .as("call %d should be allowed", i + 1).isTrue();
        }
    }

    @Test
    void burst_deniesOnceAtLimit() {
        for (int i = 0; i < 3; i++) limiter.tryAcquire("k", policy);
        RateLimiterDTO denied = limiter.tryAcquire("k", policy);
        assertThat(denied.isAllowed()).isFalse();
        assertThat(denied.getReason()).isEqualTo("window_full");
    }

    @Test
    void steadyState_remainingCountsDown() {
        assertThat(limiter.tryAcquire("k", policy).getRemainingToken()).isEqualTo(2);
        assertThat(limiter.tryAcquire("k", policy).getRemainingToken()).isEqualTo(1);
        assertThat(limiter.tryAcquire("k", policy).getRemainingToken()).isEqualTo(0);
    }

    @Test
    void deniedResponse_retryAfterIsWithinWindow() {
        for (int i = 0; i < 3; i++) limiter.tryAcquire("k", policy);
        RateLimiterDTO denied = limiter.tryAcquire("k", policy);
        assertThat(denied.getRetryAfterMs())
                .isPositive()
                .isLessThanOrEqualTo(policy.getWindowSizeMs());
    }

    @Test
    void timeBoundary_allowsAfterWindowExpires() throws InterruptedException {
        for (int i = 0; i < 3; i++) limiter.tryAcquire("k", policy);
        assertThat(limiter.tryAcquire("k", policy).isAllowed()).isFalse();

        Thread.sleep(110);   // 100 ms window + 10 ms margin

        assertThat(limiter.tryAcquire("k", policy).isAllowed()).isTrue();
    }

    @Test
    void timeBoundary_partialSlide_onlyExpiredTimestampsEvicted() throws InterruptedException {
        limiter.tryAcquire("k", policy);            // t=0
        Thread.sleep(60);
        limiter.tryAcquire("k", policy);            // t=60ms
        limiter.tryAcquire("k", policy);            // t=60ms — limit reached (3 total)
        assertThat(limiter.tryAcquire("k", policy).isAllowed()).isFalse();

        Thread.sleep(50);   // t=110ms — first timestamp (t=0) has now expired

        // One slot freed, two remain occupied
        assertThat(limiter.tryAcquire("k", policy).isAllowed()).isTrue();
        assertThat(limiter.tryAcquire("k", policy).isAllowed()).isFalse();
    }

    @Test
    void keyIsolation_separateCountsPerKey() {
        for (int i = 0; i < 3; i++) limiter.tryAcquire("key-a", policy);
        assertThat(limiter.tryAcquire("key-a", policy).isAllowed()).isFalse();
        assertThat(limiter.tryAcquire("key-b", policy).isAllowed()).isTrue();
    }

    @Test
    void concurrentAccess_neverExceedsLimit() throws InterruptedException {
        int threads = 15;
        AtomicInteger allowed = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                ready.countDown();
                try {
                    start.await();
                    if (limiter.tryAcquire("concurrent", policy).isAllowed()) allowed.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }).start();
        }

        ready.await();
        start.countDown();
        done.await(5, TimeUnit.SECONDS);

        assertThat(allowed.get()).isLessThanOrEqualTo((int) policy.getLimit());
    }
}
