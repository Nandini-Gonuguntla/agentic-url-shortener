package com.agentic.orchestrator.agent;

import com.agentic.orchestrator.workspace.Workspace;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.function.Function;

/** Renders file contents into prompts with a size budget, so context stays bounded and relevant. */
final class FileContext {

    static final int MAX_FILE_CHARS = 24_000;
    static final int MAX_TOTAL_CHARS = 120_000;

    private FileContext() {
    }

    static String render(Collection<String> paths, Workspace workspace) {
        return render(paths, p -> workspace.read(p).orElse(null));
    }

    static String render(Collection<String> paths, Map<String, String> overlay, Workspace workspace) {
        return render(paths, p -> overlay.containsKey(p) ? overlay.get(p) : workspace.read(p).orElse(null));
    }

    private static String render(Collection<String> paths, Function<String, String> reader) {
        StringBuilder out = new StringBuilder();
        for (String path : new LinkedHashSet<>(paths)) {
            String content;
            try {
                content = reader.apply(path);
            } catch (SecurityException e) {
                continue;
            }
            if (content == null) {
                out.append("\n### ").append(path).append(" (does not exist yet)\n");
                continue;
            }
            if (content.length() > MAX_FILE_CHARS) {
                content = content.substring(0, MAX_FILE_CHARS) + "\n... [truncated]";
            }
            if (out.length() + content.length() > MAX_TOTAL_CHARS) {
                out.append("\n### ").append(path).append(" (omitted: context budget reached)\n");
                continue;
            }
            out.append("\n### ").append(path).append("\n```\n").append(content).append("\n```\n");
        }
        return out.toString();
    }
}
