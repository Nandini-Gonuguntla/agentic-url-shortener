package com.agentic.shortener.service;

import com.agentic.shortener.config.ShortenerProperties;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Random Base62 codes from a CSPRNG. Random (not sequential) codes cannot be enumerated;
 * 7 characters give 62^7 (about 3.5 trillion) combinations, so collisions are rare and retried.
 */
@Component
public class CodeGenerator {

    static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    private final SecureRandom random = new SecureRandom();
    private final int length;

    public CodeGenerator(ShortenerProperties properties) {
        this.length = properties.codeLength();
    }

    public String next() {
        char[] code = new char[length];
        for (int i = 0; i < length; i++) {
            code[i] = ALPHABET.charAt(random.nextInt(ALPHABET.length()));
        }
        return new String(code);
    }
}
