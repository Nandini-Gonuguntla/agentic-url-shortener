package com.agentic.shortener.service;

import com.agentic.shortener.config.ShortenerProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Bounded LRU cache with TTL for the hot redirect path. Keeps redirects off the database for
 * popular links. Single node only; a multi-instance deployment would swap this for Redis.
 */
@Component
public class LinkCache {

    private final int maxEntries;
    private final Duration ttl;
    private final Clock clock;
    private final Map<String, Entry> entries;

    public LinkCache(ShortenerProperties properties, Clock clock) {
        this.maxEntries = properties.cache().maxEntries();
        this.ttl = properties.cache().ttl();
        this.clock = clock;
        this.entries = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
                return size() > LinkCache.this.maxEntries;
            }
        };
    }

    public synchronized Optional<ResolvedLink> get(String code) {
        Entry entry = entries.get(code);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.expiresAt().isBefore(clock.instant())) {
            entries.remove(code);
            return Optional.empty();
        }
        return Optional.of(entry.link());
    }

    public synchronized void put(ResolvedLink link) {
        if (maxEntries == 0) {
            return;
        }
        entries.put(link.code(), new Entry(link, clock.instant().plus(ttl)));
    }

    public synchronized void evict(String code) {
        entries.remove(code);
    }

    private record Entry(ResolvedLink link, Instant expiresAt) {
    }
}
