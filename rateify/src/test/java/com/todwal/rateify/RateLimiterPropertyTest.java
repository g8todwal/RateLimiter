package com.todwal.rateify;

import com.todwal.rateify.Constants.Algorithm;
import com.todwal.rateify.SlidingWindowCounter.SlidingWindowCounter;
import com.todwal.rateify.SlidingWindowLog.SlidingWindowLog;
import com.todwal.rateify.TokenBucket.TokenBucket;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.assertj.core.api.Assertions;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

/**
 * Property-based tests using jqwik.
 *
 * Why jqwik instead of (or alongside) JUnit unit tests?
 * ---
 * Unit tests verify specific examples you thought of. jqwik automatically
 * generates hundreds of (capacity, requestCount) combinations and runs the
 * same invariant against all of them. This catches edge cases that wouldn't
 * occur to a human test author — e.g. capacity=1, requests=1 or
 * capacity=50, requests=49.
 *
 * jqwik also shrinks failures: when it finds a counter-example it reduces it
 * to the smallest (capacity, requests) pair that still breaks the invariant,
 * making the root cause immediately obvious.
 */
class RateLimiterPropertyTest {

    // ── Token Bucket ────────────────────────────────────────────────────────

    @Property(tries = 200)
    void tokenBucket_neverExceedsCapacity(
            @ForAll @IntRange(min = 1, max = 50) int capacity,
            @ForAll @IntRange(min = 1, max = 200) int requests) {

        TokenBucket bucket = new TokenBucket();
        // refillRate=0.001 → no refill within a single fast test run (< 1000s)
        RateLimiterPolicy policy = policyForBucket(capacity, 0.001);

        long allowed = IntStream.range(0, requests)
                .filter(i -> bucket.tryAcquire("key", policy).isAllowed())
                .count();

        Assertions.assertThat(allowed).isLessThanOrEqualTo(capacity);
    }

    @Property(tries = 100)
    void tokenBucket_concurrent_neverExceedsCapacity(
            @ForAll @IntRange(min = 1, max = 10) int capacity,
            @ForAll @IntRange(min = 2, max = 16) int threads) throws InterruptedException {

        TokenBucket bucket = new TokenBucket();
        RateLimiterPolicy policy = policyForBucket(capacity, 0.001);
        AtomicInteger allowed = new AtomicInteger();

        runConcurrently(threads, () -> {
            if (bucket.tryAcquire("key", policy).isAllowed()) allowed.incrementAndGet();
        });

        Assertions.assertThat(allowed.get()).isLessThanOrEqualTo(capacity);
    }

    // ── Sliding Window Log ──────────────────────────────────────────────────

    @Property(tries = 200)
    void slidingWindowLog_neverExceedsLimit(
            @ForAll @IntRange(min = 1, max = 20) int limit,
            @ForAll @IntRange(min = 1, max = 100) int requests) {

        SlidingWindowLog limiter = new SlidingWindowLog();
        // Large window so no timestamps expire during the test run
        RateLimiterPolicy policy = policyForWindow(Algorithm.SLIDING_WINDOW_LOG, limit, 60_000);

        long allowed = IntStream.range(0, requests)
                .filter(i -> limiter.tryAcquire("key", policy).isAllowed())
                .count();

        Assertions.assertThat(allowed).isLessThanOrEqualTo(limit);
    }

    @Property(tries = 100)
    void slidingWindowLog_concurrent_neverExceedsLimit(
            @ForAll @IntRange(min = 1, max = 10) int limit,
            @ForAll @IntRange(min = 2, max = 16) int threads) throws InterruptedException {

        SlidingWindowLog limiter = new SlidingWindowLog();
        RateLimiterPolicy policy = policyForWindow(Algorithm.SLIDING_WINDOW_LOG, limit, 60_000);
        AtomicInteger allowed = new AtomicInteger();

        runConcurrently(threads, () -> {
            if (limiter.tryAcquire("key", policy).isAllowed()) allowed.incrementAndGet();
        });

        Assertions.assertThat(allowed.get()).isLessThanOrEqualTo(limit);
    }

    // ── Sliding Window Counter ──────────────────────────────────────────────

    @Property(tries = 200)
    void slidingWindowCounter_withinSingleWindow_neverExceedsLimit(
            @ForAll @IntRange(min = 1, max = 20) int limit,
            @ForAll @IntRange(min = 1, max = 100) int requests) {

        SlidingWindowCounter limiter = new SlidingWindowCounter();
        RateLimiterPolicy policy = policyForWindow(Algorithm.SLIDING_WINDOW_COUNTER, limit, 60_000);

        long allowed = IntStream.range(0, requests)
                .filter(i -> limiter.tryAcquire("key", policy).isAllowed())
                .count();

        // Within a single window the estimate is exact (no approximation applies)
        Assertions.assertThat(allowed).isLessThanOrEqualTo(limit);
    }

    @Property(tries = 100)
    void slidingWindowCounter_concurrent_neverExceedsLimit(
            @ForAll @IntRange(min = 1, max = 10) int limit,
            @ForAll @IntRange(min = 2, max = 16) int threads) throws InterruptedException {

        SlidingWindowCounter limiter = new SlidingWindowCounter();
        RateLimiterPolicy policy = policyForWindow(Algorithm.SLIDING_WINDOW_COUNTER, limit, 60_000);
        AtomicInteger allowed = new AtomicInteger();

        runConcurrently(threads, () -> {
            if (limiter.tryAcquire("key", policy).isAllowed()) allowed.incrementAndGet();
        });

        Assertions.assertThat(allowed.get()).isLessThanOrEqualTo(limit);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private RateLimiterPolicy policyForBucket(long capacity, double refillRate) {
        return RateLimiterPolicy.builder()
                .algorithm(Algorithm.TOKEN_BUCKET)
                .bucketCapacity(capacity)
                .refillRate(refillRate)
                .build();
    }

    private RateLimiterPolicy policyForWindow(Algorithm algo, long limit, long windowSizeMs) {
        return RateLimiterPolicy.builder()
                .algorithm(algo)
                .limit(limit)
                .windowSizeMs(windowSizeMs)
                .build();
    }

    private void runConcurrently(int threads, Runnable task) throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                ready.countDown();
                try {
                    start.await();
                    task.run();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }).start();
        }

        ready.await();
        start.countDown();
        done.await(10, TimeUnit.SECONDS);
    }
}
