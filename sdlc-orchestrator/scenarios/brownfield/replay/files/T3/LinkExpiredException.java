package com.agentic.shortener.service;

/** The link exists but its expiry instant has passed. */
public class LinkExpiredException extends RuntimeException {

    public LinkExpiredException(String message) {
        super(message);
    }
}
