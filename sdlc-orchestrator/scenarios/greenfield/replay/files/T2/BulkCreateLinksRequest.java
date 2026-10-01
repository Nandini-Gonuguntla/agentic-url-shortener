package com.agentic.shortener.api;

import com.agentic.shortener.service.BulkLinkService;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Only the batch shape is validated here. Item content is validated per item by the domain
 * rules so that one bad item becomes one REJECTED result instead of a 400 for the whole batch.
 */
public record BulkCreateLinksRequest(
        @NotNull @Size(min = 1, max = BulkLinkService.MAX_ITEMS) List<CreateLinkRequest> items) {
}
