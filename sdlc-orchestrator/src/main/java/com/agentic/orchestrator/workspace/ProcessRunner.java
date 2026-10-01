package com.agentic.orchestrator.workspace;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Runs external tools with a hard timeout and bounded output capture. No shell is involved. */
public class ProcessRunner {

    private static final int MAX_OUTPUT_CHARS = 200_000;

    public Result run(List<String> command, Path directory, Duration timeout, Map<String, String> env) {
        ProcessBuilder builder = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true);
        builder.environment().putAll(env);
        long start = System.nanoTime();
        Process process;
        try {
            process = builder.start();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot start " + command.getFirst(), e);
        }
        StringBuilder output = new StringBuilder();
        Thread reader = Thread.ofVirtual().start(() -> drain(process.getInputStream(), output));
        try {
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
            }
            reader.join(Duration.ofSeconds(5));
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            synchronized (output) {
                return new Result(finished ? process.exitValue() : -1, output.toString(), !finished, durationMs);
            }
        } catch (InterruptedException e) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while running " + command.getFirst(), e);
        }
    }

    private static void drain(InputStream in, StringBuilder output) {
        byte[] buffer = new byte[8192];
        try (in) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                synchronized (output) {
                    output.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
                    if (output.length() > MAX_OUTPUT_CHARS) {
                        output.delete(0, output.length() - MAX_OUTPUT_CHARS);
                    }
                }
            }
        } catch (IOException ignored) {
            // process ended or was killed
        }
    }

    public record Result(int exitCode, String output, boolean timedOut, long durationMs) {
        public boolean ok() {
            return exitCode == 0 && !timedOut;
        }

        public String tail(int lines) {
            String[] all = output.split("\\R");
            int from = Math.max(0, all.length - lines);
            return String.join("\n", java.util.Arrays.copyOfRange(all, from, all.length));
        }
    }
}
