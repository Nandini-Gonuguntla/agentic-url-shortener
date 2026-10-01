package com.agentic.shortener.service;

import com.agentic.shortener.domain.Link;
import com.agentic.shortener.domain.LinkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
public class LinkService {

    private static final Logger log = LoggerFactory.getLogger(LinkService.class);
    static final int MAX_CODE_ATTEMPTS = 5;

    private final LinkRepository links;
    private final CodeGenerator codeGenerator;
    private final UrlValidator urlValidator;
    private final AliasPolicy aliasPolicy;
    private final LinkCache cache;
    private final Clock clock;

    public LinkService(LinkRepository links, CodeGenerator codeGenerator, UrlValidator urlValidator,
                       AliasPolicy aliasPolicy, LinkCache cache, Clock clock) {
        this.links = links;
        this.codeGenerator = codeGenerator;
        this.urlValidator = urlValidator;
        this.aliasPolicy = aliasPolicy;
        this.cache = cache;
        this.clock = clock;
    }

    public Link create(String rawUrl, String customAlias) {
        return create(rawUrl, customAlias, null);
    }

    /**
     * Creates a short link. The unique constraint on {@code code} is the source of truth:
     * a concurrent insert of the same code surfaces as a constraint violation, which is retried
     * for generated codes and reported as a conflict for custom aliases.
     * Deliberately not {@code @Transactional}: each insert attempt runs in its own transaction.
     *
     * @param ttl time to live; null means the link never expires
     */
    public Link create(String rawUrl, String customAlias, Duration ttl) {
        String targetUrl = urlValidator.validate(rawUrl);
        Instant now = clock.instant();
        Instant expiresAt = ttl == null ? null : now.plus(ttl);
        if (customAlias != null && !customAlias.isBlank()) {
            return createWithAlias(targetUrl, customAlias.strip(), now, expiresAt);
        }
        for (int attempt = 1; attempt <= MAX_CODE_ATTEMPTS; attempt++) {
            String code = codeGenerator.next();
            if (links.existsByCode(code)) {
                continue;
            }
            try {
                return links.saveAndFlush(new Link(code, targetUrl, now, expiresAt));
            } catch (DataIntegrityViolationException race) {
                log.warn("Code collision on insert, retrying (attempt {}/{})", attempt, MAX_CODE_ATTEMPTS);
            }
        }
        throw new IllegalStateException("Could not allocate a unique code after " + MAX_CODE_ATTEMPTS + " attempts");
    }

    private Link createWithAlias(String targetUrl, String alias, Instant now, Instant expiresAt) {
        aliasPolicy.validate(alias);
        if (links.existsByCode(alias)) {
            throw new AliasAlreadyExistsException("Alias '" + alias + "' is already in use");
        }
        try {
            return links.saveAndFlush(new Link(alias, targetUrl, now, expiresAt));
        } catch (DataIntegrityViolationException race) {
            throw new AliasAlreadyExistsException("Alias '" + alias + "' is already in use");
        }
    }

    @Transactional(readOnly = true)
    public Link get(String code) {
        return links.findByCode(code).orElseThrow(() -> notFound(code));
    }

    /** Hot path for redirects: cache first, then the database. */
    @Transactional(readOnly = true)
    public ResolvedLink resolve(String code) {
        return cache.get(code).orElseGet(() -> {
            Link link = links.findByCode(code).orElseThrow(() -> notFound(code));
            if (link.isExpiredAt(clock.instant())) {
                throw new LinkExpiredException("Link '" + code + "' expired at " + link.getExpiresAt());
            }
            ResolvedLink resolved = new ResolvedLink(link.getId(), link.getCode(), link.getTargetUrl(), link.getExpiresAt());
            cache.put(resolved);
            return resolved;
        });
    }

    @Transactional
    public void delete(String code) {
        Link link = links.findByCode(code).orElseThrow(() -> notFound(code));
        links.delete(link);
        cache.evict(code);
    }

    private static LinkNotFoundException notFound(String code) {
        return new LinkNotFoundException("No link found for code '" + code + "'");
    }
}
