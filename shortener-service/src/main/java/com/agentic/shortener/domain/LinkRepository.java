package com.agentic.shortener.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

public interface LinkRepository extends JpaRepository<Link, Long> {

    Optional<Link> findByCode(String code);

    boolean existsByCode(String code);

    /**
     * Atomic increment in the database, so concurrent redirects never lose updates
     * (no read-modify-write in application memory).
     */
    @Modifying
    @Transactional
    @Query("update Link l set l.clickCount = l.clickCount + 1, l.lastAccessedAt = :at where l.id = :id")
    int recordAccess(@Param("id") Long id, @Param("at") Instant at);
}
