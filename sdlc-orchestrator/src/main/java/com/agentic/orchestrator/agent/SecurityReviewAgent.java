package com.agentic.orchestrator.agent;

import com.agentic.orchestrator.artifact.Finding;
import com.agentic.orchestrator.artifact.SecurityReport;
import com.agentic.orchestrator.artifact.Severity;
import com.agentic.orchestrator.engine.StageOutcome;
import com.agentic.orchestrator.policy.PolicyEngine;
import com.agentic.orchestrator.policy.PolicyViolation;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reviews the cumulative change (baseline..HEAD) with the policy engine's content rules plus
 * API-specific checks. Deterministic on purpose: security gates should not depend on model mood.
 */
public class SecurityReviewAgent implements Agent {

    private static final Pattern REQUEST_BODY = Pattern.compile("@RequestBody\\b");

    private final PolicyEngine policy;

    public SecurityReviewAgent(PolicyEngine policy) {
        this.policy = policy;
    }

    @Override
    public String name() {
        return "security-review";
    }

    @Override
    public StageOutcome execute(AgentContext ctx) {
        List<String> changed = ctx.workspace().changedFiles(ctx.workspace().baseline(), "HEAD");
        List<Finding> findings = new ArrayList<>();
        int reviewed = 0;
        for (String path : changed) {
            String content = ctx.workspace().read(path).orElse(null);
            if (content == null) {
                continue;
            }
            reviewed++;
            for (PolicyViolation v : policy.scanContent(path, content)) {
                findings.add(new Finding(v.severity(), v.rule(), v.path(), v.line(), v.message()));
            }
            if (path.startsWith("src/main/") && path.endsWith(".java")) {
                Matcher matcher = REQUEST_BODY.matcher(content);
                while (matcher.find()) {
                    // @Valid may sit before or after @RequestBody on the same parameter.
                    String window = content.substring(Math.max(0, matcher.start() - 40),
                            Math.min(content.length(), matcher.end() + 20));
                    if (!window.contains("@Valid")) {
                        int line = content.substring(0, matcher.start()).split("\\R", -1).length;
                        findings.add(new Finding(Severity.HIGH, "validated-input", path, line,
                                "@RequestBody without @Valid: request input is not validated"));
                    }
                }
            }
        }
        long high = findings.stream().filter(f -> f.severity().atLeast(Severity.HIGH)).count();
        String summary = reviewed + " changed file(s) reviewed, " + findings.size() + " finding(s), " + high + " high or critical";
        return StageOutcome.success(new SecurityReport(summary, reviewed, findings), "policy-engine");
    }
}
