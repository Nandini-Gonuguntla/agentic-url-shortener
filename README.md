# Agentic SDLC Orchestrator: URL Shortener

A working prototype that turns a requirement into a **reviewable engineering outcome**, meaning a
tested patch, a PR description and an engineering summary, by running AI agents through a governed,
stateful software delivery workflow. The system it builds on is a production-style URL shortener.

```
agentic-url-shortener/
├── shortener-service/     The product: Spring Boot URL shortener (APIs, analytics, reliability)
├── sdlc-orchestrator/     The agentic engine: DAG, gates, approvals, recovery, policy, audit, metrics
│   ├── scenarios/         Greenfield, brownfield and ambiguous scenarios plus recorded agent responses
│   └── src/main/resources/static/index.html   Web dashboard for humans in the loop
├── docs/                  Architecture, scenarios, testing approach, trade-offs
└── runs/                  Created at run time: one isolated workspace, audit log and output per run
```

**Principle:** agents execute inside defined autonomy boundaries; humans own approvals and final quality.

## What it demonstrates

| Requirement | Where |
|---|---|
| Requirement understanding and ambiguity detection | `RequirementsAgent`, `requirements-clarity` gate, the ambiguous scenario |
| Task decomposition with dependencies | `PlanningAgent` builds a task DAG; `plan-valid` gate checks it is acyclic and covers every criterion |
| Brownfield codebase reasoning | `CodebaseScanner` (static facts) plus `CodebaseAnalysisAgent` (impact judgement) |
| Orchestration with an explicit DAG, entry and exit gates, parallel branches and joins | `WorkflowGraph`, `RunExecutor`, `StandardGates` |
| Cross-stage context and decision lineage | `ArtifactStore` (versioned, hashed), `DecisionRecord` |
| Human approval for high-impact actions | Risk-based design approval, policy-driven approval before writing migrations or config, mandatory release sign-off |
| Bounded retries, fallback, rollback, safe stop | `RunExecutor.handleFailure`, rework loop, git checkpoints, kill switch and budgets |
| Policy guardrails (security, compliance, change control) | `policy/*`: path boundaries, protected paths, immutable migrations, secrets, dangerous APIs, PII in logs |
| Audit-grade observability | Hash-chained JSONL audit log with trace and span ids; tamper detection |
| Reliability metrics | Success rate, retry and rework rate, rollback frequency, MTTR, end-to-end latency |
| Dynamic re-planning | `Replanner` inserts threat-model and migration-safety stages; clarifications and overrides invalidate downstream work |
| Production-quality output | Every generated change is compiled and tested by the real Maven build in an isolated workspace |

## Quick start

Requirements: JDK 21 and git. Maven is not needed because the repository ships the Maven wrapper.

**Windows (PowerShell)**

```powershell
cd "C:\path\to\agentic-url-shortener"
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"     # must point at a JDK 21
.\mvnw.cmd -q -f sdlc-orchestrator\pom.xml spring-boot:run "-Dspring-boot.run.arguments=--demo=all"
```

**macOS and Linux**

```bash
./mvnw -q -f sdlc-orchestrator/pom.xml spring-boot:run -Dspring-boot.run.arguments=--demo=all
```

`--demo` accepts `greenfield`, `brownfield`, `ambiguous` or `all`. Add `--approvals=interactive`
to answer the questions and approve or reject each checkpoint yourself in the terminal. Runs take
about 1 to 2 minutes each, mostly spent running the generated code's test suite.

### Dashboard

```bash
./mvnw -q -f sdlc-orchestrator/pom.xml spring-boot:run
```

Open <http://localhost:8090>, pick a scenario and leave **Auto-approve** unchecked. The run pauses
for clarifying questions and approvals; you can approve, reject with feedback (the comment goes to the
agent), choose which upstream stage to send work back to, abort, or press **Safe stop** at any time.
The workflow graph updates live, including stages inserted by re-planning.

### Live model mode

By default the agents **replay recorded responses** (`sdlc-orchestrator/scenarios/*/replay`), so the
demo is deterministic and needs no API key. Everything else is real: prompts are built, outputs are
schema-validated, gates run, the code is compiled and tested, and git checkpoints are created.

To use Claude for real (Java SDK, structured outputs, `claude-opus-5-5` by default):

```bash
export ANTHROPIC_API_KEY=sk-ant-...
./mvnw -q -f sdlc-orchestrator/pom.xml spring-boot:run
```

In live mode you can also submit **any custom requirement** from the dashboard. If a live call fails
after the SDK's retries, the engine falls back to a recorded response when one exists and marks it
`degraded` in the audit trail. Server-side refusal fallback (`fallbacks: "default"`) is enabled and
can be turned off with `orchestrator.llm.server-side-fallback=false`.

### Output of a run

`runs/<run-id>/`

| Path | Content |
|---|---|
| `output/change.patch` | The reviewable diff against the baseline |
| `output/COMMITS.txt` | One checkpoint commit per task |
| `output/PR_DESCRIPTION.md` | Ready-to-paste PR description with validation evidence |
| `output/ENGINEERING_SUMMARY.md` | Plan and rationale, artifacts, risks and trade-offs, validation, assumptions, limitations, decision log |
| `output/decision-log.json` | Decision lineage |
| `audit.jsonl` | Hash-chained audit trail (`GET /api/runs/{id}/audit/verify`) |
| `artifacts/*.vN.json` | Every artifact version produced |
| `workspace/` | The isolated git repository the agents worked in |

## Running the tests

```bash
./mvnw -q test                                  # shortener (41 tests) + orchestrator (32 tests)
./mvnw -q -f sdlc-orchestrator/pom.xml test -Pe2e   # full pipeline for all three scenarios (about 2 min)
```

## How this was built (AI usage)

As encouraged in the brief, I built this with an AI coding assistant (Claude Code) as a pair
programmer, the same way I would on the job. The project follows the principle it demonstrates:
the AI did much of the typing; I set direction, made the calls and verified the result.

- **Direction and decisions I owned:** the stack (Java 21 and Spring Boot), the scope, and the
  trade-offs documented in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md#6-key-decisions). The most
  important one is that agents only propose changes and the engine applies them after policy gates
  and approvals.
- **What the AI generated:** most of the code, tests, recorded agent responses and documentation
  drafts, iterated through review.
- **How the output was verified, not trusted:** 73 unit and integration tests, plus end-to-end
  runs of all three scenarios in which the generated code is compiled and tested by the real Maven
  build. I also checked the pushed repository from a fresh clone. Bugs found this way were fixed:
  a Windows `cmd.exe` path issue in the Maven runner, an H2 reserved word, a false positive in the
  input-validation security check, and a `java.net.URI` edge case for numeric hosts.
- **Inside the product, AI is governed the same way:** agents work within autonomy boundaries,
  and every model call, gate decision and human approval lands in a tamper-evident audit trail.

## Documentation

- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md): components, orchestration model, control flow, key decisions
- [docs/SCENARIOS.md](docs/SCENARIOS.md): the three scenarios, with decomposition, orchestration and validation for each
- [docs/TESTING_AND_TRADEOFFS.md](docs/TESTING_AND_TRADEOFFS.md): testing approach, limitations and trade-offs
- [shortener-service/README.md](shortener-service/README.md) and [shortener-service/docs/openapi.yaml](shortener-service/docs/openapi.yaml): the product and its API

## Troubleshooting

- **`JAVA_HOME environment variable is not defined correctly`**: set `JAVA_HOME` to the JDK folder, not to `...\bin`.
- **Port 8090 in use**: add `--server.port=8091` to the run arguments.
- **"No recorded response ... need live mode"**: custom requirements need `ANTHROPIC_API_KEY`.
