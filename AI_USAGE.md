# AI_USAGE.md

This document records significant AI-assisted engineering work.

It is not a raw prompt transcript. Each entry summarizes:
- what was asked,
- significant suggestions,
- what was accepted, modified, rejected, or deferred,
- why the final decision was made,
- how the result is or will be verified.

Routine autocomplete, formatting, trivial boilerplate, and minor naming suggestions are omitted.
Canonical design documents remain the source of current system truth.

---

## AI-001 — Requirements interpretation and domain boundary

**Date:** 2026-09-03

### Context

The project needed a coherent domain boundary before implementation. The main ambiguity was whether Supplier
integration itself should become a Bounded Context and how Catalog/Search should be separated.

### Asked

- Compare possible Bounded Context boundaries for Catalog, Search, and Supplier Integration.
- Evaluate whether Supplier Integration should be a business context or an ACL/outbound boundary.
- Review the Property/RoomType Aggregate boundary.

### AI Suggestions

- Keep Catalog and Search as the actual business Bounded Contexts.
- Treat Supplier Integration as an ACL/outbound adapter boundary.
- Use `Property` as Aggregate Root with `RoomType` as an Entity.
- Expose a narrow Catalog Published Read Contract to Search instead of repositories/entities.

### Accepted

- Catalog / Search Bounded Context split.
- Supplier Integration as supporting ACL rather than a Bounded Context.
- Property Aggregate Root with RoomType Entity.
- Narrow Catalog → Search Published Contract.

### Human Decision

The split was accepted because Catalog owns stable identity/lifecycle while Search owns transient live offers.
Supplier-specific protocols do not introduce an independent business lifecycle and therefore remain outside the
business domain model.

### Verification

- Module dependency rules documented in `docs/ARCHITECTURE.md`.
- Boundary rules documented in `docs/DOMAIN.md` and `docs/USE_CASES.md`.
- Static architecture verification scenarios maintained in `docs/VERIFICATION.md`.

---

## AI-002 — Catalog snapshot reconciliation and lifecycle

**Date:** 2026-09-03

### Context

Supplier Catalog APIs return complete snapshots, so synchronization needed explicit semantics for stable internal
IDs, deactivation, reactivation, and failure.

### Asked

- Define reconciliation rules for existing/new/missing Supplier products.
- Decide whether failed or invalid snapshots may infer deletion.
- Review transaction scope for applying one Supplier snapshot.

### AI Suggestions

- Treat a fully valid Supplier Catalog response as a complete snapshot.
- Preserve internal IDs for repeated identities.
- Mark missing existing items INACTIVE instead of deleting them.
- Reactivate reappearing identities with the same internal IDs.
- On failed/invalid snapshot, preserve previous state and never infer absence.
- Perform Supplier HTTP outside the DB transaction, then apply one valid Supplier snapshot in one transaction.

### Accepted

All of the above.

### Human Decision

The snapshot policy was chosen because Supplier identity is stable and the Catalog API represents the Supplier's
current handled set. Failure is not evidence of absence, so deactivation is allowed only after a successful,
fully validated snapshot.

### Verification

- Catalog lifecycle scenarios are tracked in `docs/VERIFICATION.md`.
- PostgreSQL integration tests are required for transaction/constraint behavior.
- Supplier I/O outside transaction is an architecture guardrail.

---

## AI-003 — Search outcome and partial-failure semantics

**Date:** 2026-09-03

### Context

Integrated Search must preserve useful results when one Supplier or one batch fails, while still distinguishing a
legitimate empty search from a search that could not be completed.

### Asked

- Define complete / partial / unavailable result semantics.
- Decide how item-level malformed data differs from batch/Supplier failure.
- Decide how public HTTP status should map to integrated outcomes.

### AI Suggestions

- Keep valid sibling items when one item is malformed.
- Keep successful batches when another batch fails.
- Return a partial result when at least one relevant Supplier execution succeeds.
- Return unavailable only when every relevant Supplier execution fails.
- Keep public failure detail coarse and avoid leaking Supplier protocol/status details.

### Accepted

- Partial-success preservation.
- Batch/item failure distinction.
- `200 COMPLETE/PARTIAL` versus `503 SEARCH_UNAVAILABLE`.
- Public degraded Supplier list without raw upstream detail.

### Modified

The public contract was kept intentionally smaller than the internal failure taxonomy. Internal failure reasons
remain available for diagnostics, while the API exposes only degraded/unavailable Supplier facts.

### Verification

- Application and web-contract scenarios are maintained in `docs/VERIFICATION.md`.
- Supplier A HTTP failures and Supplier B logical failures are tested independently.

---

## AI-004 — Catalog bootstrap baseline and Search readiness

**Date:** 2026-09-03

### Context

A semantic gap was found: on a fresh database, if Catalog synchronization fails, Search could see zero targets and
incorrectly return a normal empty result.

### Asked

- Compare startup policies for partial Catalog availability.
- Distinguish a legitimately empty Catalog from an unknown Catalog caused by failed bootstrap.
- Decide whether the concern belongs in Search domain or application/operational state.

### AI Suggestions

- Introduce a per-Supplier usable Catalog baseline concept.
- Current sync success establishes a baseline.
- Sync failure may use previous persisted Catalog state as a stale baseline.
- Sync failure with no previous state means no usable baseline.
- Require every configured Supplier to have a baseline before accepting Search traffic.
- Keep bootstrap/readiness outside `SearchOutcome`.

### Accepted

- `known empty Catalog != unknown Catalog`.
- Usable baseline policy.
- Stale Catalog mapping fallback.
- Search gate before normal Search execution.
- No `SearchOutcome.NOT_READY`.

### Modified

Readiness state alone was considered insufficient for direct endpoint calls. The final design also requires the
application/web path to reject Search with `503 SEARCH_UNAVAILABLE` while the bootstrap gate is closed.

### Deferred

A persistent Supplier Catalog synchronization metadata table was deferred. The current implementation accepts the
edge case where a historically successful but always-empty Catalog cannot be distinguished after restart from a
never-initialized Catalog if the next sync fails.

### Verification

- Bootstrap/readiness cross-module scenarios are tracked in `docs/VERIFICATION.md`.
- Direct HTTP call while gate is closed must return 503 rather than 200 empty.

---

## AI-005 — Supplier identity representation and extension model

**Date:** 2026-09-03

### Context

Supplier routing uses `Map<SupplierId, Port>`. The representation needed type safety without making Supplier A/B a
closed domain set.

### Asked

- Compare raw `String`, enum, sealed type, and string-backed value class.
- Evaluate Supplier C extension impact.
- Decide where `SupplierId` should live.

### AI Suggestions

- Prefer a string-backed `@JvmInline value class`.
- Keep `SupplierId` in `:catalog:api`.
- Let Composition Root registration define currently supported Suppliers.
- Validate Supplier capability wiring at startup.

### Accepted

- `@JvmInline value class SupplierId(val value: String)`.
- Non-blank representation invariant only.
- Composition Root as supported-Supplier registry.
- Startup fail-fast for missing/inconsistent Catalog/Availability adapter bindings.

### Modified

A central `SupplierIds` registry was not adopted. Supplier A/B constants are not added to core domain types.

### Rejected

- Raw `String` because it loses semantic type safety.
- Closed `enum class SupplierId { A, B }` because it makes the common type own the supported Supplier set.

### Verification

- Static architecture review checks SupplierId ownership/representation.
- Wiring tests cover configured Supplier capability mismatches.
- Supplier C can be represented without modifying the identity type.

---

## AI-006 — Unified model information-retention policy

**Date:** 2026-09-03

### Context

Supplier A exposes nightly price/tax detail while Supplier B exposes only a whole-stay gross total. A common model
needed explicit rules for information preservation and intentional loss.

### Asked

- Decide what Supplier information should be preserved, derived, validated-only, or discarded.
- Avoid inventing fields missing from one Supplier.
- Decide metadata authority between Catalog and live availability responses.

### AI Suggestions

Classify fields as:

```text
PRESERVE
DERIVE / NORMALIZE
VALIDATE THEN DROP
DISCARD
```

Use the smallest truthful common Search semantics rather than the richest Supplier payload.

### Accepted

- Whole-stay total + currency as the common price meaning.
- Supplier A nightly price/tax used for derivation, then omitted from the common Search model.
- Supplier B `taxIncluded` used to validate gross semantics, then dropped.
- Never fabricate Supplier B nightly/tax detail.
- Daily inventory retained transiently, then reduced to whole-stay availability.
- Catalog remains authoritative for names/max occupancy.
- External Supplier codes and raw protocol errors stay out of the public API.

### Human Decision

Information loss was accepted only where downstream Search does not require the detail and another Supplier cannot
provide equivalent semantics truthfully.

### Verification

- Supplier normalization tests verify A/B price semantics.
- Search tests verify Catalog metadata authority.
- Web-contract tests verify no external codes/nightly breakdown/raw errors leak publicly.

---

## AI-007 — Broader domain scope without speculative modeling

**Date:** 2026-09-03

### Context

The design needed to demonstrate awareness of the broader relevant domain without inventing unsupported
Reservation/Payment/Pricing models.

### Asked

- Interpret the requirement to design the broader domain while implementing only the core flow.
- Decide how to document adjacent capabilities without pre-creating Bounded Contexts.
- Separate business-domain evolution from infrastructure evolution.

### AI Suggestions

Organize scope into:

```text
Current Core
Adjacent Domain Capabilities
Infrastructure / Integration Evolution
Explicitly Unmodeled Product Concepts
Explicit Out of Scope
```

Document boundary triggers rather than fixed future tactical models.

### Accepted

- Catalog/Search remain the only current business Bounded Contexts.
- Canonicalization, Reservation, and currency conversion are adjacent capabilities with revisit triggers.
- Retry/circuit-breaker/cache/quarantine are integration/infrastructure evolution.
- Physical Room, persistent RatePlan, and persistent Search History remain unmodeled without identity/lifecycle requirements.
- Explicit OOS remains separate.

### Rejected

Pre-designing speculative `Reservation`, `Pricing`, `Canonicalization`, or similar Gradle modules/Bounded Contexts
without concrete requirements.

### Verification

- `docs/DOMAIN.md` contains the scope/evolution map.
- Architecture documentation remains limited to actual modules.
- Documentation review checks that future BCs are not prematurely fixed.

---

## AI-008 — Thousands-scale batching and bounded concurrency

**Date:** 2026-09-03

### Context

Supplier availability APIs accept at most 50 Property codes per request. At thousands-scale, batching strategy,
fan-out protection, and latency semantics needed explicit reasoning.

### Asked

- Justify the initial batch-concurrency value.
- Decide whether the concurrency limiter should be per Search request or Supplier-wide.
- Analyze the relationship between per-request timeout and total Supplier execution latency.

### AI Suggestions

- Keep `maxBatchConcurrency=5` as a conservative, configurable initial value.
- Treat it as a tuning default, not an optimum/SLA/protocol constant.
- Share one limiter across all Searches using the same Supplier adapter instance.
- Distinguish per-request timeout from complete Supplier execution latency.
- Consider Supplier-level execution deadline/budget only as a production evolution.

### Accepted

- Default `5`.
- Supplier-global limit per application instance.
- `50 x 5 = up to 250` Property targets represented in active outbound batch requests per Supplier.
- Per-Supplier independent configuration.
- Production tuning based on latency, timeout, rate-limit, and failure metrics.
- `per-request timeout != Supplier execution deadline`.

### Deferred

Supplier-level execution deadline/budget, fairness, distributed/global rate limiting, and admission control are
deferred until production scale requires them.

### Verification

- Concurrency tests must observe aggregate in-flight requests across multiple concurrent customer Searches.
- Configuration binding verifies default `5`.
- Verification explicitly avoids claiming one response-timeout interval as total Supplier latency.

---

# Ongoing Recording Rule

Add a new entry only when AI materially affects at least one of:

- architecture/domain decision,
- implementation strategy,
- non-obvious debugging/root-cause analysis,
- transaction/concurrency/resilience decision,
- verification/test strategy,
- meaningful scope trade-off.

Do not add entries for routine formatting, syntax completion, trivial boilerplate, commit-message wording, or
ordinary file creation.

Do not manufacture rejected ideas or failed implementations retrospectively. Record only decisions and events that
actually occurred.

---

## AI-009 — Public repository documentation boundary

**Date:** 2026-09-03

### Context

The repository needs detailed project-owned design documentation while avoiding redistribution or effective
reconstruction of non-project external reference material.

### Asked

- Define the boundary between legitimate implementation documentation and external-reference redistribution.
- Review whether provenance-specific requirement classes should remain in a public repository.
- Strengthen repository checks beyond the current working tree.

### AI Suggestions

- Treat `REQUIREMENTS.md` as the project's current contract rather than a mirror of external provenance.
- Rename provenance-specific classifications to repository-neutral project classifications.
- Keep protocol facts required to explain adapters, but avoid exhaustive external field dictionaries/reference payload reproduction.
- Check complete Git history in addition to the current tree.
- If restricted material or secrets ever entered history, remove them through history rewrite rather than a later delete-only commit.

### Accepted

- Provenance-specific requirement classes were replaced with repository-neutral `REQUIRED`, `OPTIONAL`, and
  `OUT-OF-SCOPE` classes.
- Public documentation is written from implemented behavior/design inward.
- `INTEGRATION.md` remains detailed where protocol semantics determine implementation behavior.
- Current-tree and full-history repository safety checks are both mandatory.

### Modified

The integration documentation is not reduced to vague prose. Exact endpoints, limits, authentication, and
failure/price semantics remain documented when they materially define implemented adapter behavior.

### Human Decision

The repository should be understandable and self-contained as an engineering project without becoming a
replacement copy of external reference material. Implementation artifacts and project-authored reasoning are
kept; provenance-specific wording and unnecessary source reconstruction are removed.

### Verification

- `docs/VERIFICATION.md` includes current-tree and Git-history review scenarios.
- Documentation review checks for reconstructed external field dictionaries/payloads.
- Public-artifact restricted-string scan is run on the assembled package.

---

## AI-010 — Agent checkpoint and engineering-record workflow

**Date:** 2026-09-03

### Context

The desired workflow is not autonomous full-project completion. Implementation should proceed in coherent slices,
with meaningful engineering decisions, problems, and AI-assisted reasoning surfaced at natural checkpoints.

### Asked

- Identify what the existing agent harness still needs before implementation starts.
- Prevent an agent from automatically continuing through the entire project.
- Define when engineering work deserves Journal/AI records.
- Preserve meaningful commit history without committing per file.
- Decide whether full Requirement→Verification mapping must be completed before coding.

### AI Suggestions

- Add a checkpoint/stop rule after one coherent implementation slice.
- Report Requirement IDs, `V-*` IDs, evidence, engineering findings, record candidates, and commit boundary.
- Use event-driven criteria for `JOURNAL.md` and `AI_USAGE.md`.
- Build exact Requirement→`V-*` mapping progressively as slices are closed.
- Recommend, but do not automatically perform, coherent behavior+verification commits.

### Accepted

All five workflow changes.

### Human Decision

The agent should maximize implementation leverage without hiding the developer's judgment process. The workflow
therefore stops at meaningful engineering boundaries, exposes evidence and non-obvious findings, and leaves the
next major slice to an explicit user decision.

### Verification

- `AGENTS.md` contains checkpoint/stop, record-candidate, progressive traceability, and commit-boundary rules.
- `docs/VERIFICATION.md` includes agent-harness review scenarios.
- Canonical document paths in `AGENTS.md` resolve to `docs/...` files.

