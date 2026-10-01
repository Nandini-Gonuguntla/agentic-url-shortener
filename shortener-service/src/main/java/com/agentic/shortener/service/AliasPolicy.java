package com.agentic.shortener.service;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Rules for user-chosen custom aliases. */
@Component
public class AliasPolicy {

    private static final Pattern ALIAS = Pattern.compile("^[A-Za-z0-9_-]{3,32}$");

    /** Paths owned by the service itself; an alias must never shadow them. */
    private static final Set<String> RESERVED = Set.of("api", "actuator", "health", "admin", "login", "static");

    public void validate(String alias) {
        if (!ALIAS.matcher(alias).matches()) {
            throw new InvalidAliasException("Alias must be 3-32 characters of letters, digits, '_' or '-'");
        }
        if (RESERVED.contains(alias.toLowerCase(Locale.ROOT))) {
            throw new InvalidAliasException("Alias '" + alias + "' is reserved");
        }
    }
}
