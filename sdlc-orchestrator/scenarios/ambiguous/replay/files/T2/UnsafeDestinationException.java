package com.agentic.shortener.service;

/** The destination is well formed but refused by the safety policy. Messages never include the URL. */
public class UnsafeDestinationException extends RuntimeException {

    public UnsafeDestinationException(String message) {
        super(message);
    }
}
