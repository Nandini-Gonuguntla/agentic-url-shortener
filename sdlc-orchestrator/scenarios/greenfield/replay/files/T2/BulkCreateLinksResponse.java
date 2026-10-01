package com.agentic.shortener.api;

import java.util.List;

public record BulkCreateLinksResponse(int requested, int created, int failed, List<ItemResult> results) {

    public enum Status { CREATED, REJECTED }

    /** {@code link} is set for CREATED items; {@code problemType} and {@code message} for REJECTED ones. */
    public record ItemResult(int index, Status status, LinkResponse link, String problemType, String message) {
    }
}
