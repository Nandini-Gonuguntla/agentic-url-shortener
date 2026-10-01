package com.agentic.orchestrator.observability;

import java.security.SecureRandom;
import java.util.HexFormat;

/** W3C-trace-context-shaped ids so audit events can be correlated with external tracing later. */
public final class Ids {

    private static final SecureRandom RANDOM = new SecureRandom();

    private Ids() {
    }

    public static String traceId() {
        return random(16);
    }

    public static String spanId() {
        return random(8);
    }

    public static String shortId() {
        return random(3);
    }

    private static String random(int bytes) {
        byte[] buffer = new byte[bytes];
        RANDOM.nextBytes(buffer);
        return HexFormat.of().formatHex(buffer);
    }
}
