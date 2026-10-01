package com.agentic.shortener.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Typed, validated configuration. Invalid values fail fast at startup instead of at request time.
 */
@Validated
@ConfigurationProperties(prefix = "shortener")
public record ShortenerProperties(
        @NotBlank String baseUrl,
        @Min(5) @Max(16) int codeLength,
        @Min(32) @Max(8192) int maxUrlLength,
        @Valid @NotNull RateLimit rateLimit,
        @Valid @NotNull Cache cache,
        @Valid @NotNull Analytics analytics) {

    public record RateLimit(@Min(1) int capacity, @Min(1) int refillPerMinute) {
    }

    public record Cache(@Min(0) int maxEntries, @NotNull Duration ttl) {
    }

    public record Analytics(boolean async, @Min(1) int queueCapacity, @NotBlank String visitorSalt) {
    }
}
