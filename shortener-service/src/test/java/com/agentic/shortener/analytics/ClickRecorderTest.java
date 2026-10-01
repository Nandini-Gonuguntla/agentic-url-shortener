package com.agentic.shortener.analytics;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClickRecorderTest {

    @Test
    void keepsOnlyTheReferrerHost() {
        assertThat(ClickRecorder.referrerHost("https://News.Example.com/a?token=secret")).isEqualTo("news.example.com");
    }

    @Test
    void ignoresMissingOrMalformedReferrers() {
        assertThat(ClickRecorder.referrerHost(null)).isNull();
        assertThat(ClickRecorder.referrerHost("  ")).isNull();
        assertThat(ClickRecorder.referrerHost("not a url")).isNull();
    }
}
