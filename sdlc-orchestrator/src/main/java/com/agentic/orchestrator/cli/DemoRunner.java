package com.agentic.orchestrator.cli;

import com.agentic.orchestrator.engine.ApprovalDecision;
import com.agentic.orchestrator.engine.ApprovalRequest;
import com.agentic.orchestrator.engine.ApprovalVerdict;
import com.agentic.orchestrator.engine.Question;
import com.agentic.orchestrator.engine.RunOptions;
import com.agentic.orchestrator.engine.RunSnapshot;
import com.agentic.orchestrator.engine.RunState;
import com.agentic.orchestrator.engine.WorkflowEngine;
import com.agentic.orchestrator.observability.AuditEvent;
import com.agentic.orchestrator.scenario.ScenarioCatalog;
import com.agentic.orchestrator.scenario.ScenarioDefinition;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Headless demo: {@code --demo=greenfield|brownfield|ambiguous|all [--approvals=auto|interactive]}.
 * Streams the audit trail as a timeline, prompts for approvals in interactive mode, prints the outcome.
 */
@Component
public class DemoRunner implements ApplicationRunner {

    private static final Set<String> QUIET = Set.of("GATE_EVALUATED", "ARTIFACT_STORED", "LLM_CALL");

    private final WorkflowEngine engine;
    private final ScenarioCatalog scenarios;
    private final ConfigurableApplicationContext context;
    private final PrintStream out = System.out;

    public DemoRunner(WorkflowEngine engine, ScenarioCatalog scenarios, ConfigurableApplicationContext context) {
        this.engine = engine;
        this.scenarios = scenarios;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!args.containsOption("demo")) {
            return;
        }
        String which = args.getOptionValues("demo").getFirst();
        boolean interactive = args.containsOption("approvals") && args.getOptionValues("approvals").getFirst().equals("interactive");
        boolean verbose = args.containsOption("verbose");
        List<ScenarioDefinition> selected = which.equals("all") ? scenarios.list()
                : List.of(scenarios.find(which).orElseThrow(() -> new IllegalArgumentException("Unknown scenario " + which
                + "; available: " + scenarios.list().stream().map(ScenarioDefinition::id).toList())));
        boolean allSucceeded = true;
        for (ScenarioDefinition scenario : selected) {
            allSucceeded &= runOne(scenario, interactive, verbose);
        }
        out.println("\nReliability metrics: " + engine.services().metrics().snapshot());
        if (!args.containsOption("keep-alive")) {
            int code = allSucceeded ? 0 : 1;
            System.exit(SpringApplication.exit(context, () -> code));
        }
    }

    private boolean runOne(ScenarioDefinition scenario, boolean interactive, boolean verbose) throws Exception {
        banner("Scenario: " + scenario.title() + " [" + scenario.type() + "]");
        out.println(scenario.requirement());
        RunState run = engine.start(scenario, interactive ? RunOptions.manual() : RunOptions.auto());
        out.println("Run " + run.runId() + " (" + (interactive ? "interactive approvals" : "auto approvals") + ")\n");
        long lastSeq = 0;
        Set<String> prompted = new HashSet<>();
        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        while (!run.completion().isDone()) {
            for (AuditEvent event : run.audit().events()) {
                if (event.seq() > lastSeq) {
                    lastSeq = event.seq();
                    if (verbose || !QUIET.contains(event.type())) {
                        out.println(format(event));
                    }
                }
            }
            if (interactive) {
                promptHuman(run, stdin, prompted);
            }
            Thread.sleep(200);
        }
        for (AuditEvent event : run.audit().events()) {
            if (event.seq() > lastSeq && (verbose || !QUIET.contains(event.type()))) {
                out.println(format(event));
            }
        }
        RunSnapshot result = run.snapshot();
        banner("Result: " + result.status() + " in " + Duration.ofMillis(result.durationMs()).toSeconds() + "s"
                + (result.statusReason().isBlank() ? "" : " - " + result.statusReason()));
        out.println("Plan executed: " + result.stages().stream().map(s -> s.id() + "=" + s.status()
                + (s.executions() > 1 ? "x" + s.executions() : "")).toList());
        out.println("Counters: " + result.counters());
        out.println("Audit chain: " + run.audit().verify().message());
        out.println("Output: " + result.outputDirectory());
        return result.status() == com.agentic.orchestrator.engine.RunStatus.SUCCEEDED;
    }

    private void promptHuman(RunState run, BufferedReader stdin, Set<String> prompted) throws Exception {
        RunSnapshot snapshot = run.snapshot();
        for (Question question : snapshot.pendingQuestions()) {
            if (!prompted.add(question.stageId() + ":" + question.id())) {
                continue;
            }
            out.println("\n?? " + question.id() + ": " + question.question());
            for (int i = 0; i < question.options().size(); i++) {
                out.println("   " + (i + 1) + ") " + question.options().get(i)
                        + (question.options().get(i).equals(question.recommendedOption()) ? "  (recommended)" : ""));
            }
            out.print("   choose [1-" + question.options().size() + "] or type an answer: ");
            String line = stdin.readLine();
            String answer = line == null || line.isBlank() ? question.recommendedOption()
                    : line.matches("\\d+") ? question.options().get(Math.min(question.options().size(), Integer.parseInt(line)) - 1)
                    : line;
            Map<String, String> answers = new LinkedHashMap<>();
            for (Question q : snapshot.pendingQuestions()) {
                if (q.stageId().equals(question.stageId())) {
                    answers.put(q.id(), q.id().equals(question.id()) ? answer : q.recommendedOption());
                }
            }
            engine.answer(run.runId(), question.stageId(), answers, "cli-user");
            return;
        }
        for (ApprovalRequest request : snapshot.pendingApprovals()) {
            if (!prompted.add(request.id())) {
                continue;
            }
            out.println("\n>> APPROVAL " + request.id() + " for stage '" + request.stageId() + "' (risk " + request.riskLevel()
                    + ", artifact " + request.artifactRef() + ")");
            request.reasons().forEach(r -> out.println("   - " + r));
            out.print("   [a]pprove / [r]eject with comment / a[b]ort: ");
            String line = stdin.readLine();
            String choice = line == null ? "a" : line.strip();
            ApprovalVerdict verdict = choice.startsWith("r") ? ApprovalVerdict.REJECT
                    : choice.startsWith("b") ? ApprovalVerdict.ABORT : ApprovalVerdict.APPROVE;
            String comment = verdict == ApprovalVerdict.APPROVE ? "Approved via CLI" : choice.length() > 1
                    ? choice.substring(1).strip() : "Rejected via CLI";
            engine.decide(run.runId(), request.id(), new ApprovalDecision(verdict, "cli-user", comment, null));
            return;
        }
    }

    private static String format(AuditEvent e) {
        String stage = e.stageId() == null ? "" : String.format("%-18s", e.stageId());
        return String.format("%s  %-22s %s %s", e.timestamp().toString().substring(11, 19), e.type(), stage, summarize(e));
    }

    private static String summarize(AuditEvent e) {
        Map<String, Object> d = e.data();
        List<String> parts = new ArrayList<>();
        for (String key : List.of("agent", "task", "verdict", "reasons", "reason", "inserted", "blocks", "from", "to",
                "cycle", "commit", "testsRun", "failures", "approvalId", "risk", "answers", "nextAttempt", "artifact")) {
            if (d.containsKey(key)) {
                String value = String.valueOf(d.get(key));
                parts.add(key + "=" + (value.length() > 140 ? value.substring(0, 140) + "..." : value));
            }
        }
        return (e.actor() == null ? "" : "(" + e.actor() + ") ") + String.join(" ", parts);
    }

    private void banner(String text) {
        out.println("\n" + "=".repeat(100) + "\n" + text + "\n" + "=".repeat(100));
    }
}
