package com.agentic.shortener.analytics;

import com.agentic.shortener.domain.ClickEvent;
import com.agentic.shortener.domain.ClickEventRepository;
import com.agentic.shortener.domain.LinkRepository;
import com.agentic.shortener.service.ResolvedLink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.Executor;

/**
 * Records a click off the redirect's critical path. Failures are logged and swallowed:
 * an analytics outage must never turn into a redirect outage.
 */
@Component
public class ClickRecorder {

    private static final Logger log = LoggerFactory.getLogger(ClickRecorder.class);

    private final LinkRepository links;
    private final ClickEventRepository events;
    private final VisitorHasher visitorHasher;
    private final Executor executor;
    private final Clock clock;

    public ClickRecorder(LinkRepository links, ClickEventRepository events, VisitorHasher visitorHasher,
                         @Qualifier("clickRecordingExecutor") Executor executor, Clock clock) {
        this.links = links;
        this.events = events;
        this.visitorHasher = visitorHasher;
        this.executor = executor;
        this.clock = clock;
    }

    public void record(ResolvedLink link, String clientAddress, String referrer) {
        Instant now = clock.instant();
        String visitorHash = visitorHasher.hash(clientAddress);
        String referrerHost = referrerHost(referrer);
        executor.execute(() -> {
            try {
                links.recordAccess(link.id(), now);
                events.save(new ClickEvent(link.id(), now, referrerHost, visitorHash));
            } catch (RuntimeException e) {
                log.error("Failed to record click for code {}", link.code(), e);
            }
        });
    }

    /** Keeps only the referrer host: full referrer URLs can carry personal data in query strings. */
    static String referrerHost(String referrer) {
        if (referrer == null || referrer.isBlank()) {
            return null;
        }
        try {
            String host = URI.create(referrer.strip()).getHost();
            return host == null ? null : host.toLowerCase();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
