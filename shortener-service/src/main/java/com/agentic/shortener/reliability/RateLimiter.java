package com.agentic.shortener.reliability;

import com.agentic.shortener.config.ShortenerProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory token bucket per client key. Single node only: behind a load balancer this
 * becomes a per-instance limit, and a shared store (Redis) would be the next step.
 */
@Component
public class RateLimiter {

    static final int MAX_TRACKED_CLIENTS = 50_000;
    private static final Duration IDLE_EVICTION = Duration.ofMinutes(10);

    private final int capacity;
    private final double tokensPerNano;
    private final Clock clock;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimiter(ShortenerProperties properties, Clock clock) {
        this.capacity = properties.rateLimit().capacity();
        this.tokensPerNano = properties.rateLimit().refillPerMinute() / (double) Duration.ofMinutes(1).toNanos();
        this.clock = clock;
    }

    /** Consumes one token; returns zero when allowed, otherwise how long to wait before retrying. */
    public Duration tryAcquire(String clientKey) {
        long now = nanoTime();
        if (buckets.size() > MAX_TRACKED_CLIENTS) {
            evictIdle(now);
        }
        return buckets.computeIfAbsent(clientKey, k -> new Bucket(capacity, now)).tryConsume(now);
    }

    private void evictIdle(long now) {
        long idleNanos = IDLE_EVICTION.toNanos();
        buckets.values().removeIf(bucket -> now - bucket.lastSeen > idleNanos);
    }

    private long nanoTime() {
        Instant now = clock.instant();
        return now.getEpochSecond() * 1_000_000_000L + now.getNano();
    }

    private final class Bucket {
        private double tokens;
        private long lastRefill;
        private volatile long lastSeen;

        Bucket(int tokens, long now) {
            this.tokens = tokens;
            this.lastRefill = now;
            this.lastSeen = now;
        }

        synchronized Duration tryConsume(long now) {
            tokens = Math.min(capacity, tokens + (now - lastRefill) * tokensPerNano);
            lastRefill = now;
            lastSeen = now;
            if (tokens >= 1) {
                tokens -= 1;
                return Duration.ZERO;
            }
            long waitNanos = (long) Math.ceil((1 - tokens) / tokensPerNano);
            return Duration.ofNanos(waitNanos);
        }
    }
}
