package com.agentic.shortener;

import com.agentic.shortener.config.ShortenerProperties;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Shared fixtures for plain unit tests (no Spring context). */
public final class TestProperties {

    private TestProperties() {
    }

    public static ShortenerProperties defaults() {
        return withRateLimit(20, 60);
    }

    public static ShortenerProperties withRateLimit(int capacity, int refillPerMinute) {
        return new ShortenerProperties("http://sho.rt", 7, 2048,
                new ShortenerProperties.RateLimit(capacity, refillPerMinute),
                new ShortenerProperties.Cache(100, Duration.ofMinutes(10)),
                new ShortenerProperties.Analytics(false, 100, "test-salt"));
    }

    /** A clock tests can move forward explicitly. */
    public static final class MutableClock extends Clock {
        private Instant now;

        public MutableClock(Instant start) {
            this.now = start;
        }

        public void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
