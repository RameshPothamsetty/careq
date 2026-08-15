package com.careq.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal in-memory fixed-window rate limiter (Day 17 — brute-force /
 * abuse protection on the public auth endpoints).
 *
 * <p>{@code tryAcquire(key, max, window)} returns true while the caller is
 * within {@code max} attempts in the sliding-aligned fixed window, false
 * once exceeded. A periodic sweep drops stale buckets so the map can't grow
 * without bound.
 *
 * <p>Trade-off (documented): counters live per-instance, so with more than
 * one replica behind the gateway the limit is per-pod, not global. CareQ
 * runs auth-service with a single replica on Azure (scale-to-zero), so the
 * limit is effectively global there; a multi-replica deploy should move the
 * counters to Redis.
 */
@Component
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    private record Bucket(long windowStartMillis, int count) {
    }

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    /**
     * @return true if the caller may proceed, false if the limit is exceeded
     */
    public boolean tryAcquire(String key, int maxAttempts, Duration window) {
        long now = System.currentTimeMillis();
        long windowMillis = window.toMillis();

        Bucket updated = buckets.compute(key, (k, bucket) -> {
            if (bucket == null || now - bucket.windowStartMillis >= windowMillis) {
                return new Bucket(now, 1);
            }
            return new Bucket(bucket.windowStartMillis, bucket.count + 1);
        });

        return updated.count() <= maxAttempts;
    }

    /** Drops buckets that have been idle longer than their window. */
    @Scheduled(fixedDelay = 600_000)
    public void sweep() {
        long now = System.currentTimeMillis();
        buckets.entrySet().removeIf(entry -> now - entry.getValue().windowStartMillis() >= 3_600_000);
        log.debug("RateLimiter sweep — {} tracked keys", buckets.size());
    }
}
