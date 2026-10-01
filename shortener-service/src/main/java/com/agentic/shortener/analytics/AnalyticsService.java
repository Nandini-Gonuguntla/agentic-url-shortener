package com.agentic.shortener.analytics;

import com.agentic.shortener.domain.ClickEventRepository;
import com.agentic.shortener.domain.Link;
import com.agentic.shortener.service.LinkService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

@Service
public class AnalyticsService {

    static final int MAX_DAYS = 90;
    static final int TOP_REFERRERS = 5;

    private final LinkService linkService;
    private final ClickEventRepository events;
    private final Clock clock;

    public AnalyticsService(LinkService linkService, ClickEventRepository events, Clock clock) {
        this.linkService = linkService;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public LinkStats stats(String code, int days) {
        int window = Math.clamp(days, 1, MAX_DAYS);
        Link link = linkService.get(code);
        List<LinkStats.DailyClicks> byDay = events
                .countClicksByDay(link.getId(), clock.instant().minus(Duration.ofDays(window)))
                .stream()
                .map(row -> new LinkStats.DailyClicks(toLocalDate(row[0]), ((Number) row[1]).longValue()))
                .toList();
        List<LinkStats.ReferrerCount> referrers = events
                .topReferrers(link.getId(), PageRequest.of(0, TOP_REFERRERS))
                .stream()
                .map(row -> new LinkStats.ReferrerCount((String) row[0], ((Number) row[1]).longValue()))
                .toList();
        return new LinkStats(link.getCode(), link.getClickCount(), events.countUniqueVisitors(link.getId()),
                link.getLastAccessedAt(), byDay, referrers);
    }

    private static LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate date) {
            return date;
        }
        if (value instanceof Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        return LocalDate.parse(value.toString());
    }
}
