package com.agentic.shortener.service;

/** No link exists for the given code. */
public class LinkNotFoundException extends RuntimeException {

    public LinkNotFoundException(String message) {
        super(message);
    }
}
