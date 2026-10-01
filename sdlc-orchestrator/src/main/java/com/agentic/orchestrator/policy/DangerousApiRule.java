package com.agentic.orchestrator.policy;

import com.agentic.orchestrator.artifact.Severity;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** APIs a URL shortener has no business calling, plus hygiene issues in production code. */
public class DangerousApiRule implements ContentRule {

    private record Check(Pattern pattern, PolicyDecision decision, Severity severity, String message) {
    }

    private static final List<Check> CHECKS = List.of(
            new Check(Pattern.compile("Runtime\\.getRuntime\\(\\)\\.exec|new\\s+ProcessBuilder\\("),
                    PolicyDecision.DENY, Severity.HIGH, "Process execution from service code"),
            new Check(Pattern.compile("new\\s+ObjectInputStream\\("),
                    PolicyDecision.DENY, Severity.HIGH, "Java deserialization of untrusted data"),
            new Check(Pattern.compile("createNativeQuery\\([^)]*\\+|\"\\s*\\+\\s*\\w+\\s*\\+\\s*\"\\s*(?i)(where|and|or)"),
                    PolicyDecision.DENY, Severity.HIGH, "SQL built by string concatenation"),
            new Check(Pattern.compile("allowedOrigins\\(\"\\*\"\\)|@CrossOrigin\\(\\s*(origins\\s*=\\s*)?\"\\*\""),
                    PolicyDecision.REQUIRE_APPROVAL, Severity.MEDIUM, "Wildcard CORS"),
            new Check(Pattern.compile("\\.printStackTrace\\(\\)|System\\.(out|err)\\.print"),
                    PolicyDecision.WARN, Severity.LOW, "Use the logger instead of stdout/printStackTrace"));

    @Override
    public String id() {
        return "dangerous-apis";
    }

    @Override
    public List<PolicyViolation> scan(String path, String content) {
        if (!path.startsWith("src/main/") || !path.endsWith(".java")) {
            return List.of();
        }
        List<PolicyViolation> violations = new ArrayList<>();
        String[] lines = content.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            for (Check check : CHECKS) {
                if (check.pattern().matcher(lines[i]).find()) {
                    violations.add(new PolicyViolation(id(), check.decision(), check.severity(), path, i + 1, check.message()));
                }
            }
        }
        return violations;
    }
}
