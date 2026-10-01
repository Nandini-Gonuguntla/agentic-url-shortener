package com.agentic.shortener.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * Destination safety settings ({@code shortener.safety.*}). Kept separate from
 * {@link ShortenerProperties} so safety rules can evolve without touching core settings.
 *
 * @param blockedDomains  destinations on these domains or their subdomains are refused
 * @param blockIpLiterals refuse destinations whose host is an IP address
 */
@ConfigurationProperties(prefix = "shortener.safety")
public record SafetyProperties(List<String> blockedDomains, @DefaultValue("true") boolean blockIpLiterals) {

    public SafetyProperties {
        blockedDomains = blockedDomains == null ? List.of() : List.copyOf(blockedDomains);
    }
}
