package com.agentic.shortener.service;

import java.time.Instant;

/** The minimal data the redirect path needs; safe to cache because it holds the expiry instant, not a flag. */
public record ResolvedLink(Long id, String code, String targetUrl, Instant expiresAt) {

    public boolean isExpiredAt(Instant now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }
}
