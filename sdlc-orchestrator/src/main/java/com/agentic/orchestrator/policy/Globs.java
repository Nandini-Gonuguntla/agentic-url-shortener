package com.agentic.orchestrator.policy;

import java.util.List;
import java.util.regex.Pattern;

/** Minimal, platform-independent glob matching on forward-slash paths ({@code **}, {@code *}, {@code ?}). */
public final class Globs {

    private Globs() {
    }

    public static boolean matchesAny(String path, List<String> globs) {
        return globs.stream().anyMatch(glob -> matches(path, glob));
    }

    public static boolean matches(String path, String glob) {
        return toRegex(glob).matcher(path).matches();
    }

    static Pattern toRegex(String glob) {
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            if (c == '*') {
                if (i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                    regex.append(".*");
                    i++;
                } else {
                    regex.append("[^/]*");
                }
            } else if (c == '?') {
                regex.append("[^/]");
            } else {
                regex.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return Pattern.compile(regex.toString());
    }
}
