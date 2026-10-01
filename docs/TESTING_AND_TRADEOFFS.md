# Testing approach, limitations and trade-offs

## Testing approach

Three layers, from fast to realistic:

| Layer | What it proves | Where | Runtime |
|---|---|---|---|
| Product tests | The shortener works: validation, aliases, redirects, analytics, rate limiting, cache eviction on delete, concurrent click counting without lost updates | `shortener-service/src/test` (41 tests: unit plus `@SpringBootTest` with MockMvc) | about 40 s |
| Orchestrator unit and behaviour tests | The engine's governance guarantees, using fake agents but a **real git workspace** | `sdlc-orchestrator/src/test` (32 tests) | about 20 s |
| End-to-end scenarios | The whole pipeline: prompts, schema-typed outputs, gates, approvals, re-planning, real Maven builds of the generated code, output bundle, audit verification | `ScenarioEndToEndTest` (`-Pe2e`) and `--demo=all` | about 2 min |

Engine behaviours covered by `RunExecutorTest`:

- Parallel branches run concurrently and join before downstream stages
- Retry with feedback within the attempt budget; recovery counted for MTTR
- Fallback agent after retries are exhausted
- Quality-gate failure leads to rollback and upstream rework; the first attempt's commit is gone from history
- Rework budget exhaustion fails the run, restores the baseline and keeps `failed-attempt.patch`
- Manual approval pauses the run; rejection feeds the comment back as feedback; approval resumes
- ABORT safe-stops the run
- Writes outside the autonomy boundary are denied and nothing is applied
- Protected paths are approved **before** the change touches the workspace
- Safe stop cancels in-flight agents and discards their results
- Stage timeout is handled as a failure
- The agent-invocation budget triggers a safe stop
- The replanner inserts a stage that blocks downstream work
- Blocking questions pause the stage; answers regenerate the artifact (v2)

Also tested:
- **Graph:** cycles, unknown dependencies, atomic insert with rollback.
- **Policy:** boundary, protected paths, immutable migrations, secrets, process execution, PII in logs, change budget, glob semantics.
- **Audit:** chain validity, detection of an edited line, detection of a deleted line.
- **Replay and routing:** per-attempt fixtures, `@file` resolution, path-escape refusal, degraded fallback.

**Validation inside a run is never an agent's opinion.** `ValidationAgent` runs the project's real
build (`mvnw -B test`) and reads Surefire XML; compilation errors become structured feedback for
the implementation agent.

## Limitations

- **Replay fixtures assume the scripted answers.** In replay mode, choosing different answers to the ambiguous scenario's questions still replays the recorded v2 requirements. Arbitrary requirements and answers need live mode.
- **Live mode is not exercised by the automated tests**, because that needs an API key and is non-deterministic. The SDK integration compiles against `anthropic-java` 2.34.0 and uses structured outputs; the first live run should be watched.
- **The degraded fallback in live mode can be inconsistent.** If a live call fails mid-run, a recorded response from the same scenario may not match live upstream artifacts. The gates catch structural mismatches, and the response is marked `degraded` in the audit trail.
- **In-memory run registry.** Run state is persisted as `state.json`, artifacts and audit for inspection, but a restart does not resume in-flight runs. Metrics do persist.
- **Approver identity is self-asserted.** The optional `orchestrator.api-token` protects mutating endpoints, but there is no per-user authentication or role model (for example, so that only a DBA can approve migrations).
- **Security review is rule-based** (secrets, dangerous APIs, PII in logs, unvalidated request bodies). It complements, and does not replace, SAST, dependency scanning or human review.
- **Single machine.** One JVM, local git and a local Maven build. Workspaces are isolated by directory, not by container or sandbox.
- **Shortener scale limits.** The in-memory cache and rate limiter are per instance; H2 is for the demo. The design notes name Redis and Postgres as the next steps.
- **No deployment.** "Release readiness" produces a reviewable bundle; merging and deploying stay human actions by design.

## Trade-offs

| Choice | Benefit | Cost |
|---|---|---|
| Deterministic tool agents and gates for validation, security and migrations | Reproducible, explainable decisions | Less depth than model-based review |
| Whole-file change sets | Deterministic, conflict-free application | Larger outputs; harder to review per hunk (the final `change.patch` restores that view) |
| Rework re-runs the whole implementation stage | Simple, consistent rollback point | Re-executes tasks that were already fine |
| Risk-based approvals | Humans are asked only when it matters, and the reason is explained | Weights are judgement calls and need tuning per organisation |
| Replay as the default mode | Reliable demo and CI with no secrets | Must be kept in sync with prompts and artifact schemas |
| Bounded budgets (invocations, time, rework, wait) | Runaway agents cannot loop or burn cost | A legitimately long task may need a raised limit |
| Single-writer scheduler | Simple reasoning about state; safe cancellation | One scheduler thread per run (fine for tens of concurrent runs, not thousands) |
