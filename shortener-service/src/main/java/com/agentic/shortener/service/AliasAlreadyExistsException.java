package com.agentic.shortener.service;

/** The requested custom alias is already taken. */
public class AliasAlreadyExistsException extends RuntimeException {

    public AliasAlreadyExistsException(String message) {
        super(message);
    }
}
