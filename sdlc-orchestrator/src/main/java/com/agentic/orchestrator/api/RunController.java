package com.agentic.orchestrator.api;

import com.agentic.orchestrator.engine.ApprovalDecision;
import com.agentic.orchestrator.engine.ApprovalPolicy;
import com.agentic.orchestrator.engine.ArtifactVersion;
import com.agentic.orchestrator.engine.EngineException;
import com.agentic.orchestrator.engine.RunOptions;
import com.agentic.orchestrator.engine.RunSnapshot;
import com.agentic.orchestrator.engine.RunState;
import com.agentic.orchestrator.engine.WorkflowEngine;
import com.agentic.orchestrator.observability.AuditEvent;
import com.agentic.orchestrator.observability.AuditLog;
import com.agentic.orchestrator.scenario.ScenarioCatalog;
import com.agentic.orchestrator.scenario.ScenarioDefinition;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api")
public class RunController {

    private static final Set<String> OUTPUT_FILES = Set.of("change.patch", "COMMITS.txt", "PR_DESCRIPTION.md",
            "ENGINEERING_SUMMARY.md", "decision-log.json", "failed-attempt.patch");

    private final WorkflowEngine engine;
    private final ScenarioCatalog scenarios;

    public RunController(WorkflowEngine engine, ScenarioCatalog scenarios) {
        this.engine = engine;
        this.scenarios = scenarios;
    }

    @GetMapping("/scenarios")
    public List<ScenarioDefinition> scenarios() {
        return scenarios.list();
    }

    @PostMapping("/runs")
    public ResponseEntity<RunSnapshot> start(@RequestBody @Valid ApiModels.StartRunRequest request) {
        ScenarioDefinition scenario;
        if (request.scenarioId() != null && !request.scenarioId().isBlank()) {
            scenario = scenarios.find(request.scenarioId()).orElseThrow(() ->
                    new EngineException(EngineException.Reason.NOT_FOUND, "Unknown scenario " + request.scenarioId()));
        } else if (request.requirement() != null && !request.requirement().isBlank()) {
            scenario = ScenarioDefinition.custom(request.title(), request.requirement().strip());
        } else {
            throw new EngineException(EngineException.Reason.INVALID, "Provide scenarioId or requirement");
        }
        ApprovalPolicy approvals = "AUTO".equalsIgnoreCase(request.approvals()) ? ApprovalPolicy.AUTO : ApprovalPolicy.MANUAL;
        RunState run = engine.start(scenario, new RunOptions(approvals));
        return ResponseEntity.created(URI.create("/api/runs/" + run.runId())).body(run.snapshot());
    }

    @GetMapping("/runs")
    public List<ApiModels.RunSummary> runs() {
        return engine.list().stream().map(RunState::snapshot)
                .map(s -> new ApiModels.RunSummary(s.runId(), s.scenarioId(), s.title(), s.status(), s.statusReason(),
                        s.createdAt(), s.durationMs(), s.pendingApprovals().size(), s.pendingQuestions().size()))
                .toList();
    }

    @GetMapping("/runs/{runId}")
    public RunSnapshot run(@PathVariable String runId) {
        return engine.get(runId).snapshot();
    }

    @GetMapping("/runs/{runId}/artifacts/{stageId}")
    public Object artifact(@PathVariable String runId, @PathVariable String stageId,
                           @RequestParam(required = false) Integer version) {
        RunState run = engine.get(runId);
        return (version == null ? run.artifacts().latest(stageId) : run.artifacts().version(stageId, version))
                .map(ArtifactVersion::content)
                .orElseThrow(() -> new EngineException(EngineException.Reason.NOT_FOUND, "No artifact for " + stageId));
    }

    @PutMapping("/runs/{runId}/artifacts/{stageId}")
    public ResponseEntity<Void> override(@PathVariable String runId, @PathVariable String stageId,
                                         @RequestBody @Valid ApiModels.OverrideRequest request) {
        engine.override(runId, stageId, request.content(), request.actor(), request.reason());
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/runs/{runId}/audit")
    public List<AuditEvent> audit(@PathVariable String runId, @RequestParam(defaultValue = "0") long since) {
        return engine.get(runId).audit().events().stream().filter(e -> e.seq() > since).toList();
    }

    @GetMapping("/runs/{runId}/audit/verify")
    public AuditLog.Verification verify(@PathVariable String runId) {
        return engine.get(runId).audit().verify();
    }

    @PostMapping("/runs/{runId}/approvals/{approvalId}")
    public ResponseEntity<Void> decide(@PathVariable String runId, @PathVariable String approvalId,
                                       @RequestBody @Valid ApiModels.DecisionRequest request) {
        engine.decide(runId, approvalId, new ApprovalDecision(request.verdict(), request.approver(), request.comment(),
                request.reworkStage()));
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/runs/{runId}/answers/{stageId}")
    public ResponseEntity<Void> answer(@PathVariable String runId, @PathVariable String stageId,
                                       @RequestBody @Valid ApiModels.AnswersRequest request) {
        engine.answer(runId, stageId, request.answers(), request.actor());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/runs/{runId}/stop")
    public ResponseEntity<Void> stop(@PathVariable String runId, @RequestBody @Valid ApiModels.StopRequest request) {
        engine.stop(runId, request.reason() == null ? "Stopped by operator" : request.reason(), request.actor());
        return ResponseEntity.accepted().build();
    }

    @GetMapping(value = "/runs/{runId}/output/{file}", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> output(@PathVariable String runId, @PathVariable String file) throws IOException {
        if (!OUTPUT_FILES.contains(file)) {
            throw new EngineException(EngineException.Reason.NOT_FOUND, "Unknown output file " + file);
        }
        Path path = engine.get(runId).outputDir().resolve(file);
        if (!Files.isRegularFile(path)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Not produced yet");
        }
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(Files.readString(path));
    }

    @GetMapping("/metrics")
    public Map<String, Object> metrics() {
        return Map.of("llmMode", engine.services().llm().mode(),
                "autonomy", engine.services().limits(),
                "reliability", engine.services().metrics().snapshot());
    }
}
