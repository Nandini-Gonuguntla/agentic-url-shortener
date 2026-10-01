package com.agentic.orchestrator.artifact;

import java.util.List;

public record ValidationReport(
        boolean passed,
        int testsRun,
        int failures,
        int errors,
        int skipped,
        List<TestFailure> failedTests,
        long durationMs,
        String command,
        String outputTail) {

    public record TestFailure(String testClass, String testName, String message) {
    }
}
