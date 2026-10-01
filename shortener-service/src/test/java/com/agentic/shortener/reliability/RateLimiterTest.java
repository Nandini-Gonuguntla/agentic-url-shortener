package com.agentic.shortener.reliability;

import com.agentic.shortener.TestProperties;
import com.agentic.shortener.TestProperties.MutableClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    private final RateLimiter limiter = new RateLimiter(TestProperties.withRateLimit(3, 60), clock);

    @Test
    void allowsBurstUpToCapacityThenRejectsWithRetryHint() {
        assertThat(limiter.tryAcquire("client")).isZero();
        assertThat(limiter.tryAcquire("client")).isZero();
        assertThat(limiter.tryAcquire("client")).isZero();

        Duration wait = limiter.tryAcquire("client");

        assertThat(wait).isPositive().isLessThanOrEqualTo(Duration.ofSeconds(1));
    }

    @Test
    void refillsOverTime() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("client");
        }
        assertThat(limiter.tryAcquire("client")).isPositive();

        clock.advance(Duration.ofSeconds(1)); // 60/min = 1 token per second

        assertThat(limiter.tryAcquire("client")).isZero();
    }

    @Test
    void clientsHaveIndependentBuckets() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("a");
        }
        assertThat(limiter.tryAcquire("a")).isPositive();
        assertThat(limiter.tryAcquire("b")).isZero();
    }
}
