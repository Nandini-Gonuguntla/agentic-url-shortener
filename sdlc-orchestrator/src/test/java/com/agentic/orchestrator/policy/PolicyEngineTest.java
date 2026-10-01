package com.agentic.orchestrator.policy;

import com.agentic.orchestrator.artifact.ChangeAction;
import com.agentic.orchestrator.artifact.FileChange;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyEngineTest {

    private final PolicyEngine policy = PolicyEngine.standard();
    private final PolicyContext impl = new PolicyContext("implementation",
            List.of("src/main/java/**", "src/test/java/**", "src/main/resources/**", "pom.xml"),
            Set.of("src/main/resources/db/migration/V1__init.sql"), 3);

    private PolicyReport check(FileChange... changes) {
        return policy.evaluate(impl, List.of(changes));
    }

    private static FileChange create(String path, String content) {
        return new FileChange(path, ChangeAction.CREATE, content);
    }

    @Test
    void ordinaryCodeChangesAreAllowed() {
        assertThat(check(create("src/main/java/a/A.java", "class A {}")).decision()).isEqualTo(PolicyDecision.ALLOW);
    }

    @Test
    void writesOutsideTheBoundaryOrEscapingTheSandboxAreDenied() {
        assertThat(check(create("docs/x.md", "x")).decision()).isEqualTo(PolicyDecision.DENY);
        assertThat(check(create("../outside.txt", "x")).decision()).isEqualTo(PolicyDecision.DENY);
        assertThat(check(create(".git/config", "x")).decision()).isEqualTo(PolicyDecision.DENY);
    }

    @Test
    void newMigrationsAndConfigRequireApprovalButExistingMigrationsAreImmutable() {
        assertThat(check(create("src/main/resources/db/migration/V2__x.sql", "select 1;")).decision())
                .isEqualTo(PolicyDecision.REQUIRE_APPROVAL);
        assertThat(check(create("src/main/resources/application.yml", "a: b")).decision())
                .isEqualTo(PolicyDecision.REQUIRE_APPROVAL);
        assertThat(check(create("pom.xml", "<project/>")).decision()).isEqualTo(PolicyDecision.REQUIRE_APPROVAL);
        assertThat(check(new FileChange("src/main/resources/db/migration/V1__init.sql", ChangeAction.MODIFY, "drop table x;"))
                .decision()).isEqualTo(PolicyDecision.DENY);
    }

    @Test
    void secretsAndProcessExecutionAreDenied() {
        assertThat(check(create("src/main/java/a/K.java", "String key = \"sk-ant-abcdefghijklmnopqrstuvwxyz\";")).decision())
                .isEqualTo(PolicyDecision.DENY);
        assertThat(check(create("src/main/java/a/R.java", "Runtime.getRuntime().exec(cmd);")).decision())
                .isEqualTo(PolicyDecision.DENY);
    }

    @Test
    void loggingPersonalDataNeedsApproval() {
        PolicyReport report = check(create("src/main/java/a/L.java", "log.info(\"client {}\", request.getRemoteAddr());"));

        assertThat(report.decision()).isEqualTo(PolicyDecision.REQUIRE_APPROVAL);
        assertThat(report.violations()).extracting(PolicyViolation::rule).contains("no-pii-in-logs");
    }

    @Test
    void largeChangeSetsExceedTheAutonomousBudget() {
        PolicyReport report = check(create("src/main/java/A.java", ""), create("src/main/java/B.java", ""),
                create("src/main/java/C.java", ""), create("src/main/java/D.java", ""));

        assertThat(report.decision()).isEqualTo(PolicyDecision.REQUIRE_APPROVAL);
        assertThat(report.violations()).extracting(PolicyViolation::rule).contains("change-budget");
    }

    @Test
    void globsMatchPathSegmentsCorrectly() {
        assertThat(Globs.matches("src/main/java/a/B.java", "src/main/java/**")).isTrue();
        assertThat(Globs.matches("src/main/java", "src/*")).isFalse();
        assertThat(Globs.matches("src/main/resources/application-prod.yml", "src/main/resources/application*")).isTrue();
    }
}
