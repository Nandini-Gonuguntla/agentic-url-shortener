package com.agentic.shortener.analytics;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record LinkStats(
        String code,
        long totalClicks,
        long uniqueVisitors,
        Instant lastAccessedAt,
        List<DailyClicks> clicksByDay,
        List<ReferrerCount> topReferrers) {

    public record DailyClicks(LocalDate day, long clicks) {
    }

    public record ReferrerCount(String host, long clicks) {
    }
}
