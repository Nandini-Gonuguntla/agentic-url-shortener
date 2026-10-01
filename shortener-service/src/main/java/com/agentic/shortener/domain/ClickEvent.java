package com.agentic.shortener.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One redirect. Stores a salted visitor hash instead of the raw IP address (privacy by design).
 */
@Entity
@Table(name = "click_event")
public class ClickEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "link_id", nullable = false)
    private Long linkId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "referrer_host")
    private String referrerHost;

    @Column(name = "visitor_hash", length = 64)
    private String visitorHash;

    protected ClickEvent() {
        // for JPA
    }

    public ClickEvent(Long linkId, Instant occurredAt, String referrerHost, String visitorHash) {
        this.linkId = linkId;
        this.occurredAt = occurredAt;
        this.referrerHost = referrerHost;
        this.visitorHash = visitorHash;
    }

    public Long getId() {
        return id;
    }

    public Long getLinkId() {
        return linkId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getReferrerHost() {
        return referrerHost;
    }

    public String getVisitorHash() {
        return visitorHash;
    }
}
