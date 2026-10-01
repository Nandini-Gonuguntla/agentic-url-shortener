package com.agentic.shortener.service;

import com.agentic.shortener.domain.Link;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BulkLinkServiceTest {

    private final LinkService linkService = mock(LinkService.class);
    private final BulkLinkService bulk = new BulkLinkService(linkService);

    @Test
    void mapsEachOutcomeToAResultInRequestOrder() {
        Link created = new Link("abc1234", "https://ok.example", Instant.EPOCH);
        when(linkService.create("https://ok.example", null)).thenReturn(created);
        when(linkService.create("ftp://bad", null)).thenThrow(new InvalidUrlException("Only http and https URLs are allowed"));
        when(linkService.create("https://ok.example", "api")).thenThrow(new InvalidAliasException("Alias 'api' is reserved"));
        when(linkService.create("https://ok.example", "taken")).thenThrow(new AliasAlreadyExistsException("Alias 'taken' is already in use"));

        List<BulkItemResult> results = bulk.createAll(List.of(
                new BulkLinkService.BulkItem("https://ok.example", null),
                new BulkLinkService.BulkItem("ftp://bad", null),
                new BulkLinkService.BulkItem("https://ok.example", "api"),
                new BulkLinkService.BulkItem("https://ok.example", "taken")));

        assertThat(results).extracting(BulkItemResult::index).containsExactly(0, 1, 2, 3);
        assertThat(results.get(0)).isEqualTo(new BulkItemResult.Created(0, created));
        assertThat(results.get(1)).isInstanceOfSatisfying(BulkItemResult.Rejected.class,
                r -> assertThat(r.problemType()).isEqualTo("invalid-url"));
        assertThat(results.get(2)).isInstanceOfSatisfying(BulkItemResult.Rejected.class,
                r -> assertThat(r.problemType()).isEqualTo("invalid-alias"));
        assertThat(results.get(3)).isInstanceOfSatisfying(BulkItemResult.Rejected.class,
                r -> assertThat(r.problemType()).isEqualTo("alias-taken"));
    }

    @Test
    void nullItemsAreRejectedWithoutCallingTheService() {
        List<BulkItemResult> results = bulk.createAll(Arrays.asList((BulkLinkService.BulkItem) null));

        assertThat(results).singleElement().isInstanceOfSatisfying(BulkItemResult.Rejected.class,
                r -> assertThat(r.problemType()).isEqualTo("invalid-request"));
    }

    @Test
    void refusesBatchesOverTheLimit() {
        List<BulkLinkService.BulkItem> items = new ArrayList<>(Collections.nCopies(BulkLinkService.MAX_ITEMS + 1,
                new BulkLinkService.BulkItem("https://ok.example", null)));
        when(linkService.create(any(), any())).thenReturn(new Link("x", "https://ok.example", Instant.EPOCH));

        assertThatThrownBy(() -> bulk.createAll(items)).isInstanceOf(IllegalArgumentException.class);
    }
}
