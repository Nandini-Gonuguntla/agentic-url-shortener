package com.agentic.shortener.service;

import com.agentic.shortener.TestProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UrlValidatorTest {

    private final UrlValidator validator = new UrlValidator(TestProperties.defaults());

    @ParameterizedTest
    @ValueSource(strings = {"https://example.com", "http://example.com/a/b?q=1#frag", "HTTPS://Example.COM/path"})
    void acceptsAbsoluteHttpUrls(String url) {
        assertThat(validator.validate(url)).isEqualTo(url);
    }

    @Test
    void trimsSurroundingWhitespace() {
        assertThat(validator.validate("  https://example.com  ")).isEqualTo("https://example.com");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "example.com", "ftp://example.com", "javascript:alert(1)",
            "https://", "http://exa mple.com", "https://sho.rt/abc1234"})
    void rejectsInvalidOrUnsafeUrls(String url) {
        assertThatThrownBy(() -> validator.validate(url)).isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> validator.validate(null)).isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void rejectsUrlsOverMaxLength() {
        String longUrl = "https://example.com/" + "a".repeat(2048);

        assertThatThrownBy(() -> validator.validate(longUrl))
                .isInstanceOf(InvalidUrlException.class)
                .hasMessageContaining("2048");
    }
}
