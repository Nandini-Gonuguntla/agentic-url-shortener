package com.agentic.orchestrator.agent;

import com.agentic.orchestrator.artifact.Finding;
import com.agentic.orchestrator.artifact.MigrationReport;
import com.agentic.orchestrator.artifact.Severity;
import com.agentic.orchestrator.engine.StageOutcome;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Expand/contract discipline: new migrations must be additive so the previous release keeps working. */
public class MigrationSafetyAgent implements Agent {

    private static final Pattern NAME = Pattern.compile(".*/V(\\d+)__[A-Za-z0-9_]+\\.sql$");
    private static final Pattern DESTRUCTIVE = Pattern.compile(
            "\\b(DROP\\s+(TABLE|COLUMN|INDEX)|TRUNCATE|RENAME\\s+(TO|COLUMN)|ALTER\\s+COLUMN\\s+\\w+\\s+(SET\\s+DATA\\s+)?TYPE)\\b");
    private static final Pattern NOT_NULL_WITHOUT_DEFAULT = Pattern.compile("ADD\\s+(COLUMN\\s+)?\\w+[^;,]*NOT\\s+NULL(?![^;,]*DEFAULT)");

    @Override
    public String name() {
        return "migration-safety";
    }

    @Override
    public StageOutcome execute(AgentContext ctx) {
        int baselineMax = ctx.workspace().baselineFiles().stream().map(NAME::matcher).filter(Matcher::matches)
                .mapToInt(m -> Integer.parseInt(m.group(1))).max().orElse(0);
        List<String> added = new ArrayList<>();
        List<Finding> findings = new ArrayList<>();
        for (String path : ctx.workspace().changedFiles(ctx.workspace().baseline(), "HEAD")) {
            if (!path.contains("db/migration/")) {
                continue;
            }
            if (ctx.workspace().baselineFiles().contains(path)) {
                findings.add(new Finding(Severity.CRITICAL, "immutable-migrations", path, 0, "Applied migration was modified"));
                continue;
            }
            Matcher name = NAME.matcher(path);
            if (!name.matches()) {
                findings.add(new Finding(Severity.HIGH, "migration-naming", path, 0, "Expected V<n>__description.sql"));
                continue;
            }
            if (Integer.parseInt(name.group(1)) <= baselineMax) {
                findings.add(new Finding(Severity.HIGH, "migration-version", path, 0,
                        "Version must be greater than the latest applied version V" + baselineMax));
            }
            added.add(path);
            String sql = ctx.workspace().read(path).orElse("").toUpperCase(Locale.ROOT);
            if (DESTRUCTIVE.matcher(sql).find()) {
                findings.add(new Finding(Severity.HIGH, "additive-only", path, 0,
                        "Destructive DDL breaks the running release; use expand/contract over two releases"));
            }
            if (NOT_NULL_WITHOUT_DEFAULT.matcher(sql).find()) {
                findings.add(new Finding(Severity.HIGH, "additive-only", path, 0,
                        "NOT NULL column without DEFAULT fails on existing rows"));
            }
        }
        boolean safe = findings.stream().noneMatch(f -> f.severity().atLeast(Severity.HIGH));
        return StageOutcome.success(new MigrationReport(safe, added, findings), "migration-checker");
    }
}
