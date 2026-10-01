package com.agentic.shortener.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Creates links one by one. {@link LinkService#create} runs each insert in its own transaction,
 * so a rejected item never rolls back items created before it.
 */
@Service
public class BulkLinkService {

    public static final int MAX_ITEMS = 100;

    private final LinkService linkService;

    public BulkLinkService(LinkService linkService) {
        this.linkService = linkService;
    }

    public List<BulkItemResult> createAll(List<BulkItem> items) {
        if (items.size() > MAX_ITEMS) {
            throw new IllegalArgumentException("At most " + MAX_ITEMS + " items per request");
        }
        List<BulkItemResult> results = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            results.add(createOne(i, items.get(i)));
        }
        return results;
    }

    private BulkItemResult createOne(int index, BulkItem item) {
        if (item == null) {
            return new BulkItemResult.Rejected(index, "invalid-request", "Item must not be null");
        }
        try {
            return new BulkItemResult.Created(index, linkService.create(item.url(), item.customAlias()));
        } catch (InvalidUrlException e) {
            return new BulkItemResult.Rejected(index, "invalid-url", e.getMessage());
        } catch (InvalidAliasException e) {
            return new BulkItemResult.Rejected(index, "invalid-alias", e.getMessage());
        } catch (AliasAlreadyExistsException e) {
            return new BulkItemResult.Rejected(index, "alias-taken", e.getMessage());
        }
    }

    public record BulkItem(String url, String customAlias) {
    }
}
