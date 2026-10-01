package com.agentic.shortener.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class LinkTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void linksWithoutExpiryNeverExpire() {
        Link link = new Link("abc1234", "https://example.com", T0);

        assertThat(link.isExpiredAt(Instant.MAX)).isFalse();
    }

    @Test
    void expiresExactlyAtTheExpiryInstant() {
        Link link = new Link("abc1234", "https://example.com", T0, T0.plusSeconds(60));

        assertThat(link.isExpiredAt(T0.plusSeconds(59))).isFalse();
        assertThat(link.isExpiredAt(T0.plusSeconds(60))).isTrue();
        assertThat(link.isExpiredAt(T0.plusSeconds(61))).isTrue();
    }
}
