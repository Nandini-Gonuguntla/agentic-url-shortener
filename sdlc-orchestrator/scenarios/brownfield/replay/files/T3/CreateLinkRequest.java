package com.agentic.shortener.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** @param expiresInSeconds optional time to live; omit for links that never expire */
public record CreateLinkRequest(
        @NotBlank @Size(max = 8192) String url,
        @Size(max = 32) String customAlias,
        @Positive @Max(CreateLinkRequest.MAX_EXPIRY_SECONDS) Long expiresInSeconds) {

    /** Ten years. */
    public static final long MAX_EXPIRY_SECONDS = 315_360_000L;
}
