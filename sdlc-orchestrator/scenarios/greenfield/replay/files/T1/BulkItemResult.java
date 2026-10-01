package com.agentic.shortener.service;

import com.agentic.shortener.domain.Link;

/** Outcome of one item in a bulk request. */
public sealed interface BulkItemResult {

    int index();

    record Created(int index, Link link) implements BulkItemResult {
    }

    /** @param problemType the same {@code urn:problem:*} suffix the single-create endpoint uses */
    record Rejected(int index, String problemType, String message) implements BulkItemResult {
    }
}
