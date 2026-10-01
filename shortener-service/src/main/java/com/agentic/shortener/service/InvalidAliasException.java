package com.agentic.shortener.service;

/** The requested custom alias is not allowed. */
public class InvalidAliasException extends RuntimeException {

    public InvalidAliasException(String message) {
        super(message);
    }
}
