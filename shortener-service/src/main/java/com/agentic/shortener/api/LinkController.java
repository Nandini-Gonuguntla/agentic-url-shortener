package com.agentic.shortener.api;

import com.agentic.shortener.analytics.AnalyticsService;
import com.agentic.shortener.analytics.LinkStats;
import com.agentic.shortener.config.ShortenerProperties;
import com.agentic.shortener.domain.Link;
import com.agentic.shortener.service.LinkService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/links")
public class LinkController {

    private final LinkService linkService;
    private final AnalyticsService analyticsService;
    private final String baseUrl;

    public LinkController(LinkService linkService, AnalyticsService analyticsService, ShortenerProperties properties) {
        this.linkService = linkService;
        this.analyticsService = analyticsService;
        this.baseUrl = properties.baseUrl();
    }

    @PostMapping
    public ResponseEntity<LinkResponse> create(@Valid @RequestBody CreateLinkRequest request) {
        Link link = linkService.create(request.url(), request.customAlias());
        return ResponseEntity.created(URI.create("/api/v1/links/" + link.getCode()))
                .body(LinkResponse.from(link, baseUrl));
    }

    @GetMapping("/{code}")
    public LinkResponse get(@PathVariable String code) {
        return LinkResponse.from(linkService.get(code), baseUrl);
    }

    @GetMapping("/{code}/stats")
    public LinkStats stats(@PathVariable String code, @RequestParam(defaultValue = "30") int days) {
        return analyticsService.stats(code, days);
    }

    @DeleteMapping("/{code}")
    public ResponseEntity<Void> delete(@PathVariable String code) {
        linkService.delete(code);
        return ResponseEntity.noContent().build();
    }
}
