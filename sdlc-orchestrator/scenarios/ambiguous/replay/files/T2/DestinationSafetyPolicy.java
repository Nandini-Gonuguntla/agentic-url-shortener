package com.agentic.shortener.service;

import com.agentic.shortener.config.SafetyProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Refuses destinations that are commonly used to deceive people who click short links:
 * blocklisted domains (and their subdomains), IP-address hosts, and URLs with embedded
 * credentials that disguise the real host. Runs on creation only, with no network calls.
 */
@Component
public class DestinationSafetyPolicy {

    private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");
    /** Decimal (3232235521) and hex (0xc0a80001) hosts are IP addresses in disguise. */
    private static final Pattern NUMERIC_HOST = Pattern.compile("^(0x[0-9a-f]+|\\d+)$");

    private final Set<String> blockedDomains;
    private final boolean blockIpLiterals;

    public DestinationSafetyPolicy(SafetyProperties properties) {
        this.blockedDomains = properties.blockedDomains().stream()
                .map(DestinationSafetyPolicy::normalize)
                .filter(domain -> !domain.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        this.blockIpLiterals = properties.blockIpLiterals();
    }

    /** @param url a URL already accepted by {@link UrlValidator} */
    public void check(String url) {
        URI uri = URI.create(url);
        if (uri.getRawUserInfo() != null) {
            throw new UnsafeDestinationException("Destinations with embedded credentials are not allowed");
        }
        String host = normalize(hostOf(uri));
        if (blockIpLiterals && isIpLiteral(host)) {
            throw new UnsafeDestinationException("Destinations must use a domain name, not an IP address");
        }
        if (isBlocked(host)) {
            throw new UnsafeDestinationException("The destination domain is blocked by policy");
        }
    }

    /** Matches on label boundaries: blocks login.phishing.test, not notphishing.test. */
    private boolean isBlocked(String host) {
        String candidate = host;
        while (true) {
            if (blockedDomains.contains(candidate)) {
                return true;
            }
            int dot = candidate.indexOf('.');
            if (dot < 0) {
                return false;
            }
            candidate = candidate.substring(dot + 1);
        }
    }

    /**
     * java.net.URI reports no host for authorities it cannot parse as a hostname, such as
     * numeric hosts (http://3232235521/); fall back to the raw authority so they are still checked.
     */
    private static String hostOf(URI uri) {
        if (uri.getHost() != null) {
            return uri.getHost();
        }
        String authority = uri.getRawAuthority();
        if (authority == null) {
            return "";
        }
        String hostAndPort = authority.substring(authority.lastIndexOf('@') + 1);
        int colon = hostAndPort.lastIndexOf(':');
        return colon > 0 && !hostAndPort.endsWith("]") ? hostAndPort.substring(0, colon) : hostAndPort;
    }

    static boolean isIpLiteral(String host) {
        return host.startsWith("[") || IPV4.matcher(host).matches() || NUMERIC_HOST.matcher(host).matches();
    }

    static String normalize(String host) {
        if (host == null) {
            return "";
        }
        String lower = host.strip().toLowerCase(Locale.ROOT);
        return lower.endsWith(".") ? lower.substring(0, lower.length() - 1) : lower;
    }
}
