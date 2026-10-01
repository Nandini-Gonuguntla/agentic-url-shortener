package com.agentic.shortener.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "link")
public class Link {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(name = "target_url", nullable = false, length = 2048)
    private String targetUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Maintained with an atomic UPDATE in {@link LinkRepository#recordAccess}; never incremented in memory. */
    @Column(name = "click_count", nullable = false)
    private long clickCount;

    @Column(name = "last_accessed_at")
    private Instant lastAccessedAt;

    protected Link() {
        // for JPA
    }

    public Link(String code, String targetUrl, Instant createdAt) {
        this.code = code;
        this.targetUrl = targetUrl;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getTargetUrl() {
        return targetUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public long getClickCount() {
        return clickCount;
    }

    public Instant getLastAccessedAt() {
        return lastAccessedAt;
    }
}
