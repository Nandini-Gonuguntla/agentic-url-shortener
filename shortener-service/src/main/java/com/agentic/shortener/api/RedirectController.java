package com.agentic.shortener.api;

import com.agentic.shortener.analytics.ClickRecorder;
import com.agentic.shortener.service.LinkService;
import com.agentic.shortener.service.ResolvedLink;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
public class RedirectController {

    private final LinkService linkService;
    private final ClickRecorder clickRecorder;

    public RedirectController(LinkService linkService, ClickRecorder clickRecorder) {
        this.linkService = linkService;
        this.clickRecorder = clickRecorder;
    }

    /**
     * 302 (not 301) so browsers do not cache the redirect permanently: every click reaches
     * the service, which keeps analytics accurate and lets links be deleted or changed.
     */
    @GetMapping("/{code:[A-Za-z0-9_-]{3,32}}")
    public ResponseEntity<Void> redirect(@PathVariable String code, HttpServletRequest request) {
        ResolvedLink link = linkService.resolve(code);
        clickRecorder.record(link, request.getRemoteAddr(), request.getHeader(HttpHeaders.REFERER));
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(link.targetUrl()))
                .cacheControl(CacheControl.noStore())
                .build();
    }
}
