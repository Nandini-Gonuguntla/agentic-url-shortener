package com.agentic.orchestrator.policy;

import com.agentic.orchestrator.artifact.Severity;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Compliance: personal data (IP addresses, emails, credentials) must not be written to logs. */
public class PiiLoggingRule implements ContentRule {

    private static final Pattern LOG_CALL = Pattern.compile("\\blog(ger)?\\.(trace|debug|info|warn|error)\\(");
    private static final Pattern PII = Pattern.compile("(?i)getRemoteAddr|ipAddress|clientIp|email|password|X-Forwarded-For");

    @Override
    public String id() {
        return "no-pii-in-logs";
    }

    @Override
    public List<PolicyViolation> scan(String path, String content) {
        if (!path.endsWith(".java") || path.startsWith("src/test/")) {
            return List.of();
        }
        List<PolicyViolation> violations = new ArrayList<>();
        String[] lines = content.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            if (LOG_CALL.matcher(lines[i]).find() && PII.matcher(lines[i]).find()) {
                violations.add(new PolicyViolation(id(), PolicyDecision.REQUIRE_APPROVAL, Severity.MEDIUM, path, i + 1,
                        "Log statement may record personal data"));
            }
        }
        return violations;
    }
}
