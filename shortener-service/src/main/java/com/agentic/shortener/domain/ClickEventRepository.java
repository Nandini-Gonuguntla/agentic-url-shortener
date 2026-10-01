package com.agentic.shortener.domain;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {

    @Query("select count(distinct c.visitorHash) from ClickEvent c where c.linkId = :linkId")
    long countUniqueVisitors(@Param("linkId") Long linkId);

    @Query(value = """
            select cast(occurred_at as date) as click_day, count(*) as clicks
            from click_event
            where link_id = :linkId and occurred_at >= :since
            group by cast(occurred_at as date)
            order by click_day
            """, nativeQuery = true)
    List<Object[]> countClicksByDay(@Param("linkId") Long linkId, @Param("since") Instant since);

    @Query("""
            select c.referrerHost, count(c) from ClickEvent c
            where c.linkId = :linkId and c.referrerHost is not null
            group by c.referrerHost
            order by count(c) desc
            """)
    List<Object[]> topReferrers(@Param("linkId") Long linkId, Pageable page);
}
