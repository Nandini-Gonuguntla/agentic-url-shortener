package com.agentic.orchestrator.policy;

import com.agentic.orchestrator.artifact.Severity;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Credentials must never enter the codebase, whoever (or whatever) wrote them. */
public class SecretRule implements ContentRule {

    private static final List<Pattern> PATTERNS = List.of(
            Pattern.compile("AKIA[0-9A-Z]{16}"),
            Pattern.compile("-----BEGIN (RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----"),
            Pattern.compile("sk-ant-[A-Za-z0-9_-]{20,}"),
            Pattern.compile("gh[pousr]_[A-Za-z0-9]{30,}"),
            Pattern.compile("(?i)(password|passwd|secret|api[_-]?key|access[_-]?token)\\s*[:=]\\s*[\"'][^\"'\\s]{8,}[\"']"));

    @Override
    public String id() {
        return "no-secrets";
    }

    @Override
    public List<PolicyViolation> scan(String path, String content) {
        List<PolicyViolation> violations = new ArrayList<>();
        String[] lines = content.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            for (Pattern pattern : PATTERNS) {
                if (pattern.matcher(lines[i]).find()) {
                    violations.add(new PolicyViolation(id(), PolicyDecision.DENY, Severity.CRITICAL, path, i + 1,
                            "Possible hard-coded credential"));
                    break;
                }
            }
        }
        return violations;
    }
}
