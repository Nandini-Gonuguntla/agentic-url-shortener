package com.agentic.shortener.api;

import com.agentic.shortener.domain.Link;

import java.time.Instant;

/** {@code expiresAt} is null for links that never expire. */
public record LinkResponse(String code, String shortUrl, String targetUrl, Instant createdAt, long clickCount,
                           Instant expiresAt) {

    static LinkResponse from(Link link, String baseUrl) {
        return new LinkResponse(link.getCode(), baseUrl + "/" + link.getCode(), link.getTargetUrl(),
                link.getCreatedAt(), link.getClickCount(), link.getExpiresAt());
    }
}
