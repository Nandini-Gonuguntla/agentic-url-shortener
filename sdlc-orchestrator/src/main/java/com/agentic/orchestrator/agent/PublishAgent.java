package com.agentic.orchestrator.agent;

import com.agentic.orchestrator.artifact.PublishResult;
import com.agentic.orchestrator.engine.StageOutcome;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Writes the reviewable outcome: patch, commit list, PR description, engineering summary, decision log. */
public class PublishAgent implements Agent {

    private final ObjectMapper json;

    public PublishAgent(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public String name() {
        return "publish";
    }

    @Override
    public StageOutcome execute(AgentContext ctx) throws IOException {
        Path out = ctx.outputDir();
        Files.createDirectories(out);
        String baseline = ctx.workspace().baseline();
        List<String> changed = ctx.workspace().changedFiles(baseline, "HEAD");
        Files.writeString(out.resolve("change.patch"), ctx.workspace().diff(baseline, "HEAD"));
        Files.writeString(out.resolve("COMMITS.txt"), ctx.workspace().log(baseline));
        Reports reports = new Reports(ctx, json);
        Files.writeString(out.resolve("PR_DESCRIPTION.md"), reports.pullRequest(changed));
        Files.writeString(out.resolve("ENGINEERING_SUMMARY.md"), reports.engineeringSummary(changed));
        json.writerWithDefaultPrettyPrinter().writeValue(out.resolve("decision-log.json").toFile(), ctx.runView().get().decisions());
        List<String> files = List.of("change.patch", "COMMITS.txt", "PR_DESCRIPTION.md", "ENGINEERING_SUMMARY.md",
                "decision-log.json");
        return StageOutcome.success(new PublishResult(out.toString(), files, changed.size(), out.resolve("change.patch").toString()),
                "publisher");
    }
}
