# SDD Harness Closeout Audit Prompt

Use this prompt at the end of an AI coding / SDD task, after implementation and deterministic checks have run.

## Required context

Before auditing, read:

1. `SDD-HARNESS-WORKFLOW.md` as the decision reference, not as hard `AGENTS.md` rules.
2. The SLICE / SPEC file involved in this task.
3. The deterministic Trace Checker report, preferably produced by:

   ```bash
   python3 tools/sddh/check_trace.py --format json
   ```

4. Relevant architecture artifacts when the task changes module boundaries, public APIs, ports/adapters, or cross-module dependencies:
   - `architecture/workspace.dsl`
   - `likec4-sandbox/architecture.c4`
   - module `package-info.java` files
   - `DECISIONS.md`

## Audit stance

Do not treat this prompt as a request to implement or fix code.

The goal is to produce a closeout audit that separates deterministic findings from semantic judgment:

- Deterministic findings come from tools such as `tools/sddh/check_trace.py`, ArchUnit, Spring Modulith, and build/test commands.
- Semantic findings come from comparing spec intent, module ownership, architecture decisions, and code shape.

If deterministic tool output is unavailable, mark the result as degraded mode and explain which guarantees are missing.

## Required output format

Produce the following sections.

### 1. Scope audited

List:

- Task / SLICE / PR being audited.
- Files changed.
- Commands and reports used.
- Whether this is full audit or degraded mode.

### 2. Layer 1 — Spec ↔ BDD / AC Test Trace

Use the Trace Checker report first.

Check:

- Each `AC-###` in the relevant SLICE has a matching `ac###` test method.
- No `ac###` test method is orphaned from spec.
- Disabled AC tests are listed with reason.
- Any missing / orphan / disabled findings are classified as:
  - existing baseline debt,
  - new debt introduced by this task,
  - intentionally accepted exception requiring decision record.

Do not infer semantic AC coverage from method name alone. If semantics are unclear, flag for human review.

### 3. Layer 2 — INV ↔ UT Trace

Use the Trace Checker report first.

Check:

- Each `INV-###` in the relevant SLICE has a matching `inv###` unit-style test method.
- No `inv###` test method is orphaned from spec.
- Disabled INV tests are listed with reason.
- The test level appears appropriate for invariant verification; if an invariant is only covered through a broad integration test, flag as possible weakness.

### 4. Layer 3 — Code ↔ Modulith / ArchUnit Boundary

Check deterministic architecture results when available:

```bash
cd backend && ./mvnw test -Dtest=ArchitectureTest,ModulithVerifierTest
```

Then audit semantically:

- Cross-module references go through public root APIs or documented ports.
- Portal modules remain thin and do not own business rules.
- Application layer does not depend on infrastructure implementations.
- Domain layer remains independent of outer layers.
- Cross-cutting modules do not reverse-depend on business modules.

If the command cannot run due to environment limitations, mark it as warning, not pass.

### 5. Layer 4 — Code ↔ Architecture Artifacts Drift

Only check architecture-level facts, not every internal class.

Check whether the task changed any of these:

- module boundaries,
- module public APIs,
- ports / adapters / SEAMs,
- cross-module dependencies,
- transaction or orchestration strategy,
- architecture SOT assumptions such as Structurizr vs LikeC4.

If yes, verify whether relevant artifacts were updated:

- `architecture/workspace.dsl`
- `likec4-sandbox/architecture.c4`
- `package-info.java`
- `DECISIONS.md`

If artifacts were not updated, classify as possible drift and explain the exact missing synchronization.

### 6. Findings summary

Group findings by severity:

- **Fail**: new deterministic violation or architecture boundary break.
- **Warning**: existing baseline debt, unavailable environment check, or semantic uncertainty.
- **Info**: observations that do not require action.

For each finding, include:

- id or topic,
- evidence,
- impacted file(s),
- recommended next action,
- whether human decision is required.

### 7. Recommended next step

Recommend exactly one next step:

- merge / accept,
- fix before merge,
- create follow-up issue,
- update architecture decision record,
- rerun audit after missing environment dependency is resolved.

Do not recommend multiple competing next steps unless explicitly asked.

## Explicit non-goals

Do not:

- auto-fix code,
- invent AC / INV ids,
- require production class names to contain spec ids,
- demand every internal class appear in architecture diagrams,
- treat AI semantic judgment as equivalent to deterministic tool output,
- silently ignore baseline warnings.
