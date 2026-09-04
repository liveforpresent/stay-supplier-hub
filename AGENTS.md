# AGENTS.md

> Coding-agent entrypoint.
>
> Defines **how to work in this repository**. Detailed business, Supplier, persistence, and HTTP semantics belong to their canonical documents.

## 1. Canonical Sources

| Concern | Source |
|---|---|
| Requirements / classification | `docs/REQUIREMENTS.md` |
| Terminology | `docs/GLOSSARY.md` |
| Domain model / invariants | `docs/DOMAIN.md` |
| Application flows | `docs/USE_CASES.md` |
| Supplier protocol / failures / resilience | `docs/INTEGRATION.md` |
| Modules / dependencies / concurrency / transactions | `docs/ARCHITECTURE.md` |
| PostgreSQL / JPA / IDs | `docs/PERSISTENCE.md` |
| Public HTTP contract | `docs/API.md` |
| Acceptance / evidence | `docs/VERIFICATION.md` |
| Agent workflow | `AGENTS.md` |

`README.md`, `JOURNAL.md`, and `AI_USAGE.md` are derived/process documents and never override the sources above.

Process-document roles:

```text
README.md   → reader-facing implemented reality and major rationale
JOURNAL.md  → meaningful engineering decisions, problems, alternatives, and outcomes
AI_USAGE.md → significant AI assistance, human acceptance/modification/rejection, and verification
Git history → exact repository changes
```

If code conflicts with documentation, follow the canonical document for that concern.
If canonical documents conflict, do not invent a resolution in code. Identify the conflict, report the blocker, and keep the disputed semantic/architectural change out of the implementation.

## 2. Reading Rule

For every work unit:

1. read `AGENTS.md`,
2. identify relevant Requirement ID(s),
3. find related `V-*` scenarios in `VERIFICATION.md`,
4. read canonical docs for the affected concern,
5. inspect existing code/tests before editing.

| Work | Read |
|---|---|
| Catalog domain | `docs/DOMAIN.md`, `docs/USE_CASES.md` |
| Catalog persistence | `docs/DOMAIN.md`, `docs/USE_CASES.md`, `docs/ARCHITECTURE.md`, `docs/PERSISTENCE.md` |
| Supplier adapter | `docs/USE_CASES.md`, `docs/INTEGRATION.md`, `docs/ARCHITECTURE.md` |
| Search domain | `docs/DOMAIN.md`, `docs/USE_CASES.md` |
| Search application | `docs/DOMAIN.md`, `docs/USE_CASES.md`, `docs/INTEGRATION.md`, `docs/ARCHITECTURE.md` |
| Public API | `docs/USE_CASES.md`, `docs/API.md`, `docs/ARCHITECTURE.md` |
| Build/module/config | `docs/ARCHITECTURE.md` |
| Schema/ID | `docs/PERSISTENCE.md`, `docs/ARCHITECTURE.md` |

Use `docs/GLOSSARY.md` when terminology is unclear. Do not read every canonical document in full for every small change.

## 3. Work Unit & Workflow

Prefer one narrow requirement or verification slice. Do not widen scope for unrelated cleanup.

Good examples:
- StayAvailability invariant + Domain tests.
- Supplier B resultCode normalization + adapter tests.
- SearchOutcome → HTTP mapping + Web tests.
- Catalog projection + PostgreSQL verification.

Avoid repository-wide feature/refactor requests such as “implement all Search” or “clean up the whole repository”.

Workflow:

1. Identify Requirement ID(s).
2. Find related `V-*` scenario(s).
3. Read relevant canonical docs.
4. Inspect current implementation.
5. Identify owning module/layer.
6. Implement the smallest coherent change.
7. Add/update owning-layer verification.
8. Run focused tests, then affected module tests.
9. Run broader tests when the slice crosses boundaries.
10. Update evidence/status only after proof.
11. Update canonical docs only if approved semantics changed.
12. Record meaningful process/AI decisions when relevant:
    - engineering reasoning/problem history → `JOURNAL.md`,
    - material AI contribution + human disposition → `AI_USAGE.md`.

Required trace:

`Requirement ID → V-* scenario → owning layer → implementation → evidence`

Test-first coding is optional. Knowing the acceptance scenario and owning layer before implementation is not.

## 3.1 Checkpoint & Stop Rule

A normal user request should close **one coherent implementation slice**, not autonomously consume the entire
project roadmap.

After the requested slice is implemented and verified:

1. **stop before starting the next major slice**,
2. report the closed Requirement ID(s),
3. report the closed/affected `V-*` ID(s),
4. summarize the implementation and concrete evidence,
5. identify any non-obvious decision, issue, trade-off, hidden assumption, or unexpected verification result,
6. classify whether the work produced a `JOURNAL.md` candidate, an `AI_USAGE.md` candidate, both, or neither,
7. state whether this is a meaningful commit boundary,
8. recommend the next coherent slice **without implementing it** unless the user explicitly asks to continue.

Small supporting work required to finish the **same Requirement / V-* slice** may proceed without an intermediate
checkpoint. Examples include a DTO, mapper, fixture, or private helper needed by that slice.

Do not stop at arbitrary file/class boundaries. The checkpoint unit is a coherent behavior plus its verification.

### Checkpoint Report Shape

Use this concise structure:

```text
Closed
- Requirement IDs
- V-* IDs

What changed
- implementation summary

Engineering finding
- non-obvious decision/issue/trade-off, or "none"

Evidence
- tests/checks actually run and passed

Record candidates
- JOURNAL: yes/no + reason
- AI_USAGE: yes/no + reason

Commit boundary
- yes/no + suggested scope

Next
- next coherent slice only
```

Do not claim a Requirement or verification as closed until the corresponding evidence actually passes.

## 4. Verification Rules

`docs/VERIFICATION.md` is the Definition-of-Done source.

- `PLANNED → PASSING` only after automated evidence passes.
- `PLANNED → MANUAL-PASS` only after the manual/static check is performed.
- Never mark evidence complete because code merely exists.
- When closing mandatory work, maintain both `Requirement ID → V-*` and `V-* → concrete test/check evidence`.
- Exact Requirement → `V-*` mapping may be completed progressively as implementation slices are closed; do not
  require a speculative full-project mapping before development begins.
- Before a Requirement is marked complete, its exact `V-*` mapping must be explicit in `docs/VERIFICATION.md`
  or its implementation evidence.
- Never remove/weaken a mandatory failing scenario, disable a required test, or lower P0 priority to avoid work.

Verify at the lowest owning layer:

| Behavior | Verification |
|---|---|
| Domain invariant | Domain unit |
| Use-case decision | Application + fake Ports |
| Supplier protocol | Supplier adapter integration |
| DB/JPA | PostgreSQL integration |
| HTTP mapping | Web contract |
| Transaction/wiring | Cross-module integration |
| Critical vertical path | Small E2E set |

Do not repeat full edge-case suites at every layer.

Normal run order: focused test → affected module → related modules if needed → broader test at slice completion.

Major milestone/final gate:

```bash
./gradlew clean test
./gradlew build
```

Prefer barriers/latches/in-flight counters over arbitrary elapsed-time thresholds for concurrency tests.

## 5. Module Placement

```text
:app
:catalog:api
:catalog:domain
:catalog:port
:catalog:application
:catalog:adapter:persistence
:search:domain
:search:port
:search:application
:search:adapter:web
:integration:supplier-a
:integration:supplier-b
:shared:infrastructure
:mock-supplier
```

| Behavior | Module |
|---|---|
| Catalog Published Read Contract | `:catalog:api` |
| Catalog model | `:catalog:domain` |
| Catalog Ports | `:catalog:port` |
| Catalog orchestration | `:catalog:application` |
| JPA/PostgreSQL | `:catalog:adapter:persistence` |
| Search rules | `:search:domain` |
| Search Ports | `:search:port` |
| Search orchestration | `:search:application` |
| Controller / HTTP DTO | `:search:adapter:web` |
| Supplier DTO/WebClient/protocol | `:integration:supplier-*` |
| Domain-neutral infrastructure | `:shared:infrastructure` |
| Composition/bootstrap | `:app` |

Do not add a Gradle module for implementation convenience alone.

## 6. Hard Architecture Boundaries

Unless an approved change updates `docs/ARCHITECTURE.md`:

- Search accesses Catalog only through `:catalog:api`.
- Catalog/Search never depend on concrete Supplier modules.
- Supplier adapters depend inward on consumer-owned Ports.
- Domain/API/Port modules remain framework-free.
- Application code never handles Supplier DTO, Web DTO, or JPA Entity.
- Web adapter never depends on persistence adapter.
- `:shared:infrastructure` has no Catalog/Search dependency.
- `:app` contains composition/bootstrap, not business behavior.
- `:mock-supplier` is never a production dependency.

Gradle dependencies are the primary boundary. Never bypass them through a generic shared/common module.

## 7. Runtime, Concurrency & Transactions

| Concern | Choice |
|---|---|
| Inbound HTTP | Spring MVC |
| Supplier HTTP | WebClient |
| Concurrent I/O | Kotlin coroutines / structured concurrency |
| Persistence | blocking JPA + PostgreSQL |

For Supplier calls, do not use `RestTemplate`, `RestClient`, `WebClient.block()`, `GlobalScope`, or unbounded async fan-out.
Do not introduce full reactive server/persistence architecture without an approved change.

Search:
- materialize Catalog projection,
- group by Supplier,
- execute Supplier groups concurrently,
- isolate failures,
- combine normalized results.

Supplier batching:
- split by request limit,
- bound concurrency,
- preserve successful batches,
- normalize failed batches.

Never create one unbounded request/coroutine per Property.

Catalog sync:
- Supplier HTTP/normalization is outside the DB transaction.
- A validated complete snapshot is applied by a synchronous transactional application collaborator.
- One validated Supplier snapshot = one transaction.
- Supplier A/B transactions are independent.

Search must not hold a DB transaction while waiting on Supplier HTTP.

## 8. Supplier Integration

Supplier-specific behavior stays in `:integration:supplier-a` / `:integration:supplier-b`.

Adapters own request/response DTOs, `X-Api-Key`, WebClient config, batching, timeouts, external success/failure interpretation, response validation, and Supplier-specific price translation.

Do not leak upward:
- Supplier DTOs,
- raw HTTP status,
- Supplier `resultCode`,
- WebClient/Reactor types,
- batch indexes.

Rule: **share semantics, not shapes**. Similar DTOs alone do not justify a shared abstraction.

Focused adapter tests use a controllable local HTTP stub.
E2E uses the real `:mock-supplier`.
Never call real external Supplier services.

## 9. Persistence

PostgreSQL persists Catalog state only. Do not persist live `StayOffer`, `StayPrice`, `DailyInventory`, `StayAvailability`, raw availability responses, or Search history/results.

Internal Catalog IDs:
- `PropertyId` / `RoomTypeId` are Snowflake-backed `Long`,
- DB representation is `BIGINT`,
- Snowflake mechanism lives in `:shared:infrastructure`,
- Catalog owns semantic ID Generator Ports,
- `:app` binds them.

Use Flyway, `ddl-auto=validate`, and PostgreSQL Testcontainers.
Do not substitute H2 for PostgreSQL verification.
Keep Domain objects and JPA entities separate.

## 10. Public API Scope

Public HTTP semantics come from `API.md`.

- Do not serialize Domain/Application objects directly.
- Do not expose Supplier external Property/RoomType codes.
- Do not pass upstream HTTP semantics directly into the public contract.

Do not add features without an approved requirement, including region/keyword search, sorting, pagination, authentication, admin sync endpoints, or reservation flows.

## 11. Change Authority

### Level 1 — implementation-local

Allowed if contracts remain unchanged:
- private/internal helpers,
- mappers,
- adapter-local DTOs,
- test fixtures,
- local refactors/implementation details.

### Level 2 — contract-adjacent

Check the canonical document first:
- Port/repository implementation details,
- query/projection implementation,
- configuration properties,
- adapter behavior required by an existing contract.

If the contract itself changes, treat it as Level 3.

### Level 3 — architectural / semantic

Do not change silently:
- Bounded Context / Aggregate boundaries,
- Gradle module set / dependency direction,
- Published Contract,
- public API contract,
- persistent ownership / ID strategy,
- transaction boundary,
- Supplier failure semantics,
- requirement classification,
- new mandatory capability.

An approved Level 3 change must update the canonical document before or with implementation. Do not let code become the accidental source of truth.

## 12. Scope & Abstraction Discipline

| Classification | Rule |
|---|---|
| `HARD-CONSTRAINT` | Never violate |
| `REQUIRED` / `PROJECT-MUST` | Complete |
| `PROJECT-DECISION` | Preserve |
| `OPTIONAL` | Optional unless selected |
| `OUT-OF-SCOPE` | Do not implement |

Optional retry/circuit-breaker/cache work must not delay mandatory P0 work.

Duplication alone does not justify shared code. Do not create generic Supplier response models, shared business dumping grounds, integration-core routing layers, or common domain objects that erase ownership unless a stable shared semantic responsibility exists.

`:shared:infrastructure` is limited to narrow domain-neutral technical mechanisms.

## 13. Never Weaken the System to Pass Tests

Do not:
- weaken approved invariants,
- catch/ignore unexpected failures,
- invent fallback success,
- remove required assertions,
- disable mandatory tests,
- arbitrarily increase timeouts,
- change public contracts only to match broken code,
- replace PostgreSQL tests with a behaviorally different database.

If approved semantics are wrong or contradictory, resolve the canonical decision first.

## 14. Documentation, Process & Repository Safety

Canonical docs change only when approved semantics change.

### Record Candidate Rules

`JOURNAL.md` is a candidate when at least one meaningful engineering event occurred:

- a non-obvious architecture/domain decision was made,
- implementation exposed a hidden assumption,
- a failure/root cause changed implementation direction,
- transaction/concurrency/resilience behavior required material reasoning,
- a meaningful alternative was deliberately rejected,
- verification revealed a real defect or design gap,
- a scope/performance/consistency trade-off was made.

Do **not** create a new Journal entry merely because a previously documented design decision was implemented
successfully. Routine implementation completion is not a new engineering decision.

`AI_USAGE.md` is a candidate when AI materially affected at least one of:

- architecture/domain reasoning,
- implementation strategy,
- non-obvious debugging/root-cause analysis,
- transaction/concurrency/resilience solution,
- verification/test strategy,
- meaningful scope decision.

Do not record routine autocomplete, syntax completion, formatting, trivial boilerplate, or minor naming help.

If no meaningful record candidate exists, say so at the checkpoint instead of manufacturing one.

`README.md` is reader-facing and should describe implemented reality: build/run, system overview, and major design decisions/rationale.

Record only meaningful events.

`JOURNAL.md`:
- engineering decision/problem history,
- important alternatives and trade-offs,
- non-obvious blockers/root causes,
- important verification outcomes.

`AI_USAGE.md`:
- significant AI-assisted decisions/implementation/debugging/verification,
- what was asked,
- accepted/modified/rejected/deferred suggestions,
- human reasoning,
- final verification/evidence.

Do not duplicate routine activity across the two files. Do not use either as a daily task list or raw prompt transcript.

Never commit:
- non-project-authored external reference material that is not intended for redistribution,
- verbatim or substantially reconstructed external reference documents,
- secrets/API keys/passwords,
- restricted identifying strings prohibited by `REQUIREMENTS.md`,
- instructions intended to manipulate reviewers or automated review,
- generated code the maintainer cannot explain.

Public documentation must explain the implementation and engineering decisions in project-owned language.
Use generic public terminology such as `Supplier A`, `Supplier B`, and accommodation/stay aggregator.

Repository safety checks must cover both:
- the current tracked tree,
- the complete Git history, including commit messages, historical filenames/objects, and secrets.

Run repository checks from `VERIFICATION.md` before finalization.

### Commit Boundary

Prefer a commit boundary when one coherent behavior and its verification are complete.

Good boundaries:

```text
Catalog reconciliation + owning tests
Supplier B failure normalization + adapter tests
Search partial-failure behavior + application tests
bootstrap gate + cross-module verification
```

Avoid:

```text
one commit per file/class
one final commit for the entire project
mixing unrelated cleanup with a completed requirement slice
```

At a checkpoint, recommend the commit boundary and scope. Do not perform a Git commit unless the user explicitly
asks for it.

## 15. Stop Conditions

Do not continue the disputed semantic/architectural change when:
- canonical documents conflict,
- a mandatory requirement has no clear behavior,
- an approved dependency must be reversed,
- a new Aggregate/Bounded Context appears necessary,
- public API semantics must change,
- persistent ownership/ID strategy must change,
- transaction semantics must change,
- Supplier behavior cannot fit the approved Port,
- a mandatory test requires weakening an approved invariant.

Inspect the relevant canonical docs, state the blocker precisely, and do not apply the disputed decision implicitly.
Unrelated safe work may continue if independent.

## 16. Work-Unit Definition of Done

- [ ] Requirement ID(s) identified.
- [ ] Related `V-*` scenario(s) identified.
- [ ] Correct owning module/layer used.
- [ ] Owning-layer verification passes.
- [ ] Affected module tests pass.
- [ ] HARD-CONSTRAINT and architecture/transaction/framework guardrails remain intact.
- [ ] Exact Requirement ↔ V-* ↔ evidence mapping is updated after proof.
- [ ] Evidence/status updated only after proof.
- [ ] Canonical docs updated only if approved semantics changed.
- [ ] Meaningful process/AI record added when relevant.

Project completion is defined by the `VERIFICATION.md` Completion Gate.

## 17. Before Writing Code

Answer:

1. What requirement am I closing?
2. What verification proves it?
3. Which layer owns the behavior?
4. Which canonical document defines the semantics?

Then implement the smallest coherent change that closes that slice without weakening existing boundaries.
