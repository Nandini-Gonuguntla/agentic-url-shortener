package com.agentic.shortener.service;

import com.agentic.shortener.config.ShortenerProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

/**
 * Accepts only absolute http(s) URLs with a host, within the length limit,
 * and never pointing back at this service (which would create redirect loops).
 */
@Component
public class UrlValidator {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    private final int maxLength;
    private final String selfHost;

    public UrlValidator(ShortenerProperties properties) {
        this.maxLength = properties.maxUrlLength();
        this.selfHost = URI.create(properties.baseUrl()).getHost().toLowerCase(Locale.ROOT);
    }

    /** Returns the trimmed URL or throws {@link InvalidUrlException} with the reason. */
    public String validate(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new InvalidUrlException("URL must not be blank");
        }
        String url = rawUrl.strip();
        if (url.length() > maxLength) {
            throw new InvalidUrlException("URL exceeds " + maxLength + " characters");
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new InvalidUrlException("URL is malformed");
        }
        String scheme = uri.getScheme();
        if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT))) {
            throw new InvalidUrlException("Only http and https URLs are allowed");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new InvalidUrlException("URL must include a host");
        }
        if (host.toLowerCase(Locale.ROOT).equals(selfHost)) {
            throw new InvalidUrlException("URL must not point to this shortener");
        }
        return url;
    }
}
