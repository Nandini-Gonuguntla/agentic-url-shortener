package com.agentic.shortener.service;

/** The submitted destination URL was rejected. */
public class InvalidUrlException extends RuntimeException {

    public InvalidUrlException(String message) {
        super(message);
    }
}
