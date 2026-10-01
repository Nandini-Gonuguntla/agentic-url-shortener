package com.agentic.shortener.service;

/** The minimal data the redirect path needs; safe to cache. */
public record ResolvedLink(Long id, String code, String targetUrl) {
}
