package com.careq.auth.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * fixed-window rate limiting: allows up to max, blocks beyond,
 * and the window resets so the key recovers.
 */
class RateLimiterTest {

    private final RateLimiter rateLimiter = new RateLimiter();

    @Test
    void allowsUpToMaxAttemptsThenBlocks() {
        String key = "login:a@b.com:1.2.3.4";
        for (int i = 1; i <= 5; i++) {
            assertThat(rateLimiter.tryAcquire(key, 5, Duration.ofMinutes(15)))
                    .as("attempt %d should pass", i).isTrue();
        }
        assertThat(rateLimiter.tryAcquire(key, 5, Duration.ofMinutes(15))).isFalse();
    }

    @Test
    void differentKeysAreIndependent() {
        assertThat(rateLimiter.tryAcquire("a", 1, Duration.ofMinutes(1))).isTrue();
        assertThat(rateLimiter.tryAcquire("a", 1, Duration.ofMinutes(1))).isFalse();
        assertThat(rateLimiter.tryAcquire("b", 1, Duration.ofMinutes(1))).isTrue();
    }

    @Test
    void windowExpiryResetsTheBucket() throws InterruptedException {
        String key = "k";
        assertThat(rateLimiter.tryAcquire(key, 1, Duration.ofMillis(50))).isTrue();
        assertThat(rateLimiter.tryAcquire(key, 1, Duration.ofMillis(50))).isFalse();

        Thread.sleep(80);
        assertThat(rateLimiter.tryAcquire(key, 1, Duration.ofMillis(50))).isTrue();
    }
}
