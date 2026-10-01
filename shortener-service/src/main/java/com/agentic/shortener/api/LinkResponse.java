package com.agentic.shortener.api;

import com.agentic.shortener.domain.Link;

import java.time.Instant;

public record LinkResponse(String code, String shortUrl, String targetUrl, Instant createdAt, long clickCount) {

    static LinkResponse from(Link link, String baseUrl) {
        return new LinkResponse(link.getCode(), baseUrl + "/" + link.getCode(), link.getTargetUrl(),
                link.getCreatedAt(), link.getClickCount());
    }
}
