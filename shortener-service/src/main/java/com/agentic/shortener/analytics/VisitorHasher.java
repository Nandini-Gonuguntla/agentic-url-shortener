package com.agentic.shortener.analytics;

import com.agentic.shortener.config.ShortenerProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Turns a client address into a salted SHA-256 digest so unique visitors can be counted
 * without ever persisting raw IP addresses.
 */
@Component
public class VisitorHasher {

    private final byte[] salt;

    public VisitorHasher(ShortenerProperties properties) {
        this.salt = properties.analytics().visitorSalt().getBytes(StandardCharsets.UTF_8);
    }

    public String hash(String clientAddress) {
        if (clientAddress == null || clientAddress.isBlank()) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt);
            digest.update(clientAddress.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JDK spec", e);
        }
    }
}
