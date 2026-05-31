package com.todwal.rateify.SlidingWindowCounter;

import com.todwal.rateify.Constants.Algorithm;
import com.todwal.rateify.DTO.RateLimiterDTO;
import com.todwal.rateify.RateLimiterPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class SlidingWindowCounterTest {

    private SlidingWindowCounter limiter;
    private RateLimiterPolicy policy;

    @BeforeEach
    void setUp() {
        limiter = new SlidingWindowCounter();
        policy = RateLimiterPolicy.builder()
                .algorithm(Algorithm.SLIDING_WINDOW_COUNTER)
                .windowSizeMs(100)
                .limit(5)
                .build();
    }

    @Test
    void burst_allowsExactlyLimit() {
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryAcquire("k", policy).isAllowed())
                    .as("call %d should be allowed", i + 1).isTrue();
        }
    }

    @Test
    void burst_deniesOnceAtLimit() {
        for (int i = 0; i < 5; i++) limiter.tryAcquire("k", policy);
        RateLimiterDTO denied = limiter.tryAcquire("k", policy);
        assertThat(denied.isAllowed()).isFalse();
        assertThat(denied.getReason()).isEqualTo("window_full");
    }

    @Test
    void deniedResponse_hasPositiveRetryAfter() {
        for (int i = 0; i < 5; i++) limiter.tryAcquire("k", policy);
        RateLimiterDTO denied = limiter.tryAcquire("k", policy);
        assertThat(denied.getRetryAfterMs()).isPositive();
    }

    @Test
    void timeBoundary_allowsAfterFullWindowExpires() throws InterruptedException {
        for (int i = 0; i < 5; i++) limiter.tryAcquire("k", policy);
        assertThat(limiter.tryAcquire("k", policy).isAllowed()).isFalse();

        Thread.sleep(110);

        assertThat(limiter.tryAcquire("k", policy).isAllowed()).isTrue();
    }

    @Test
    void windowSlide_prevCountContributesProportionally() throws InterruptedException {
        // Use a wider window so OS scheduling jitter doesn't accidentally jump two windows
        RateLimiterPolicy widePolicy = RateLimiterPolicy.builder()
                .algorithm(Algorithm.SLIDING_WINDOW_COUNTER)
                .windowSizeMs(500)
                .limit(5)
                .build();

        // Fill window 1 completely
        for (int i = 0; i < 5; i++) limiter.tryAcquire("k", widePolicy);

        // Sleep 550ms: crosses the 500ms boundary into window 2 (~50ms in)
        // overlap = (500 - 50) / 500 = 0.9 → estimate = 5 * 0.9 = 4.5
        // Allows 1 request (4.5 < 5), then estimate → 5.5 → denied
        Thread.sleep(550);

        int allowed = 0;
        for (int i = 0; i < 5; i++) {
            if (limiter.tryAcquire("k", widePolicy).isAllowed()) allowed++;
        }
        // Some requests allowed (window slid) but not all (prev window still contributes)
        assertThat(allowed).isGreaterThan(0).isLessThan(5);
    }

    @Test
    void keyIsolation_separateCountsPerKey() {
        for (int i = 0; i < 5; i++) limiter.tryAcquire("key-a", policy);
        assertThat(limiter.tryAcquire("key-a", policy).isAllowed()).isFalse();
        assertThat(limiter.tryAcquire("key-b", policy).isAllowed()).isTrue();
    }

    @Test
    void concurrentAccess_withinSingleWindow_neverExceedsLimit() throws InterruptedException {
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

        // Within a single window, the counter is exact (no approximation)
        assertThat(allowed.get()).isLessThanOrEqualTo((int) policy.getLimit());
    }
}
