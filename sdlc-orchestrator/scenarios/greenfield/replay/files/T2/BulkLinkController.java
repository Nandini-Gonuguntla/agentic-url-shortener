package com.agentic.shortener.api;

import com.agentic.shortener.config.ShortenerProperties;
import com.agentic.shortener.service.BulkItemResult;
import com.agentic.shortener.service.BulkLinkService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/links/bulk")
public class BulkLinkController {

    private final BulkLinkService bulkLinkService;
    private final String baseUrl;

    public BulkLinkController(BulkLinkService bulkLinkService, ShortenerProperties properties) {
        this.bulkLinkService = bulkLinkService;
        this.baseUrl = properties.baseUrl();
    }

    /** Always 200: the batch was processed; each item carries its own status. */
    @PostMapping
    public BulkCreateLinksResponse create(@Valid @RequestBody BulkCreateLinksRequest request) {
        List<BulkLinkService.BulkItem> items = request.items().stream()
                .map(item -> item == null ? null : new BulkLinkService.BulkItem(item.url(), item.customAlias()))
                .toList();
        List<BulkCreateLinksResponse.ItemResult> results = bulkLinkService.createAll(items).stream()
                .map(this::toResponse)
                .toList();
        int created = (int) results.stream().filter(r -> r.status() == BulkCreateLinksResponse.Status.CREATED).count();
        return new BulkCreateLinksResponse(results.size(), created, results.size() - created, results);
    }

    private BulkCreateLinksResponse.ItemResult toResponse(BulkItemResult result) {
        return switch (result) {
            case BulkItemResult.Created created -> new BulkCreateLinksResponse.ItemResult(created.index(),
                    BulkCreateLinksResponse.Status.CREATED, LinkResponse.from(created.link(), baseUrl), null, null);
            case BulkItemResult.Rejected rejected -> new BulkCreateLinksResponse.ItemResult(rejected.index(),
                    BulkCreateLinksResponse.Status.REJECTED, null, "urn:problem:" + rejected.problemType(),
                    rejected.message());
        };
    }
}
