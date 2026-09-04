# REQUIREMENTS.md

> Normative source of truth for project requirements.  
> This file defines **what must hold**, not how it is implemented.
>
> Implementation details belong in `ARCHITECTURE.md`, domain reasoning in `DOMAIN.md`,
> and executable verification strategy in `VERIFICATION.md`.

---

## 1. Requirement Classes

| Class | Meaning |
|---|---|
| `REQUIRED` | Mandatory behavior or contract for this project |
| `PROJECT-MUST` | Additional engineering/quality constraint adopted by this project |
| `PROJECT-DECISION` | A deliberate choice where multiple valid designs exist |
| `OPTIONAL` | Enhancement not required for core completion |
| `OUT-OF-SCOPE` | Explicitly excluded from the current project scope |
| `HARD-CONSTRAINT` | Must never be violated |

### Completion Rule

A requirement is `DONE` only when:

- required behavior is implemented,
- automated verification exists where practical,
- required rationale/documentation exists,
- full project verification passes.

Do not change a frozen `PROJECT-DECISION` merely to simplify implementation.
A material change requires an explicit design decision record.

---

# 2. Hard Constraints

## HC-001 Public repository safety

**Class:** `HARD-CONSTRAINT`

- Do not include restricted external organization/process-identifying names or phrases in repository names, code, documentation, comments, or commit messages.
- Do not commit or reproduce non-project-authored external reference material that is not intended for redistribution.
- Public documentation must describe the project's own contract, implementation, and reasoning in original wording.
- Public documentation must not reconstruct an external reference document section-by-section, as an exhaustive field dictionary, or through large copied reference payloads.
- Project-owned implementation artifacts such as DTOs, mocks, tests, fixtures, diagrams, and protocol semantics required to explain the implementation are allowed.

---

## HC-002 No commercial Supplier access

**Class:** `HARD-CONSTRAINT`

- The implementation must not depend on real commercial Supplier services.
- Supplier integration must be verified against project-owned Mock Suppliers.

---

## HC-003 Generated-code ownership

**Class:** `HARD-CONSTRAINT`

- AI-generated code must be reviewed before merge.
- Code that cannot be explained by the developer must not remain in the repository.
- Significant AI suggestions must be accepted, modified, or rejected intentionally.

---

## HC-004 No reviewer manipulation

**Class:** `HARD-CONSTRAINT`

Do not place instructions intended to manipulate reviewers or automated review systems in:

- source code,
- documentation,
- comments,
- commit messages,
- generated artifacts.

---

# 3. Repository & Delivery

## REP-001 Public repository

**Class:** `REQUIRED`

Acceptance:

- The project repository is public.
- Source, build configuration, tests, and project-authored documentation are contained in the repository.
- The repository alone is sufficient to build and run the project.

---

## REP-003 Public documentation boundary

**Class:** `HARD-CONSTRAINT`

Public documentation is written **from the implementation inward**, not by reproducing an external reference
document outward.

Acceptance:

- `REQUIREMENTS.md` is the current project contract, not a provenance-preserving copy of external material.
- `INTEGRATION.md` documents protocol/semantic differences needed to understand the adapters.
- External field dictionaries, example payloads, and reference-document section structure are not reproduced merely for completeness.
- Exact protocol facts may be documented when they materially define implemented behavior or verification.
- `README.md`, `JOURNAL.md`, and `AI_USAGE.md` describe the project and engineering process without embedding external reference text.

---

## REP-002 Meaningful development history

**Class:** `PROJECT-MUST`

Acceptance:

- Development is not represented by a single final squashed commit.
- Major features, tests, fixes, and design changes are committed in meaningful units.
- Commit history makes the implementation process reasonably traceable.

---

# 4. Technology Baseline

## TEC-001 Kotlin / JVM

**Class:** `PROJECT-MUST`

- Kotlin is the server implementation language.
- JVM 25 is the project toolchain.
- The project must compile and test using the configured toolchain.

---

## TEC-002 Spring Boot

**Class:** `PROJECT-MUST`

- Spring Boot 4.x is used.
- The application must start successfully with the selected Kotlin/JVM combination.

---

## TEC-003 Gradle

**Class:** `REQUIRED`

- Gradle is the build system.
- Kotlin DSL is used.
- A root Gradle Wrapper can build the complete multi-module project.

---

## TEC-004 PostgreSQL

**Class:** `PROJECT-MUST`

- PostgreSQL is the persistent database.
- Supplier-to-internal identity mappings survive application restart.
- Database schema creation/migration is reproducible.

---

## TEC-005 WebClient

**Class:** `REQUIRED`

Supplier HTTP communication must use Spring `WebClient`.

Acceptance:

- Supplier calls do not use `RestTemplate` or `RestClient`.
- Supplier calls support explicit connection and response timeout control.
- Multiple Suppliers are not orchestrated through sequential blocking HTTP calls.
- The chosen MVC/WebClient programming model is documented.

---

# 5. Catalog & Identity

## CAT-001 Internal Property identity

**Class:** `REQUIRED`

Every Supplier Property must resolve to an internal `PropertyId`.

Acceptance:

- Customer-facing search results use internal Property IDs.
- Supplier Property codes are not used as internal identifiers.
- Properties from different Suppliers may have different internal IDs even when they represent the same real-world accommodation.

---

## CAT-002 Internal RoomType identity

**Class:** `REQUIRED`

Every Supplier RoomType must resolve to an internal `RoomTypeId`.

Acceptance:

- Customer-facing search results use internal RoomType IDs.
- Supplier RoomType codes are not used as internal identifiers.

---

## CAT-003 Property mapping persistence

**Class:** `REQUIRED`

External Property identity is defined by:

`Supplier + SupplierPropertyCode`

Acceptance:

- One external Property identity resolves to exactly one internal Property ID.
- The mapping is persisted in PostgreSQL.
- Duplicate mappings are prevented by database constraints.

---

## CAT-004 RoomType mapping persistence

**Class:** `REQUIRED`

External RoomType identity is defined by:

`Supplier + SupplierPropertyCode + SupplierRoomTypeCode`

Acceptance:

- The same room code may exist under different Properties without collision.
- One external RoomType identity resolves to exactly one internal RoomType ID.
- Duplicate mappings are prevented by database constraints.

---

## CAT-005 Stable internal identity

**Class:** `REQUIRED`

Acceptance:

- Re-synchronizing the same Supplier product preserves its internal Property ID.
- Re-synchronizing the same Supplier room type preserves its internal RoomType ID.
- New internal IDs are created only for previously unknown external identities.

---

## CAT-006 Catalog-driven mapping

**Class:** `REQUIRED`

Mappings must originate from Supplier catalog/list APIs.

Acceptance:

- Supplier A catalog data can create/update mappings.
- Supplier B catalog data can create/update mappings.
- Search does not depend on hard-coded Supplier Property lists.
- Search targets are derived from persisted mappings.

---

## CAT-007 Static vs dynamic data lifecycle

**Class:** `REQUIRED`

Acceptance:

- Property and RoomType mappings are persisted.
- Supplier price and inventory remain externally authoritative.
- Search retrieves current price and inventory from Suppliers rather than using persisted values as the source of truth.

---

# 6. Supplier Integration

## CON-001 Supplier authentication

**Class:** `REQUIRED`

Acceptance:

- Supplier requests include the required API-key header.
- Authentication applies to both catalog and search/availability requests.
- Credentials are provided through configuration, not hard-coded in source.
- Supplier credentials can be configured independently.

---

## CON-002 Search-condition propagation

**Class:** `REQUIRED`

The following search information must reach each Supplier request without semantic loss:

- `checkIn`
- `checkOut`
- `adults`
- `children`

Acceptance:

- `adults` and `children` remain separate values.
- Check-out date semantics are preserved.
- Supplier-specific Property parameter names are handled only inside Supplier adapters.

---

## CON-003 Supplier protocol isolation

**Class:** `REQUIRED`

Acceptance:

- Supplier-specific request/response DTOs remain inside their adapter boundary.
- Supplier field names and protocol details do not leak into Search domain models.
- Application/domain code depends on internal contracts, not Supplier response types.

---

## CON-004 Supplier A integration

**Class:** `REQUIRED`

The Supplier A adapter must support:

- catalog retrieval,
- price/inventory retrieval,
- transport-level failure interpretation,
- conversion into internal integration models.

---

## CON-005 Supplier B integration

**Class:** `REQUIRED`

The Supplier B adapter must support:

- catalog retrieval,
- price/inventory search,
- body-level success/failure interpretation,
- conversion into internal integration models.

HTTP success alone must never imply Supplier B business success.

---

## CON-006 Unified Supplier failures

**Class:** `REQUIRED`

All declared Supplier failures must be translated into internal failure semantics.

Minimum internal categories:

- `INVALID_REQUEST`
- `AUTHENTICATION_FAILED`
- `RATE_LIMITED`
- `SUPPLIER_ERROR`
- `SERVICE_UNAVAILABLE`
- `CONNECTION_FAILED`
- `TIMEOUT`
- `INVALID_RESPONSE`

Acceptance:

- Declared Supplier A error responses never produce normal offers.
- Declared Supplier B failure results never produce normal offers.
- Application code does not inspect Supplier-specific raw error codes.

---

## CON-007 Supplier extensibility

**Class:** `REQUIRED`

Acceptance:

- Adding a new Supplier is primarily an adapter/integration concern.
- Existing Supplier-specific implementations do not require modification.
- Search's unified offer contract does not become Supplier-specific.
- The expected extension path is documented.

---

## DEC-CON-001 Supplier identity representation

**Class:** `PROJECT-DECISION`

`SupplierId` is an internal type-safe identifier represented by a non-blank string-backed value object.

Acceptance:

- `SupplierId` is not modeled as a closed enum of Supplier A/B.
- The value object validates only the representation-level invariant required now: the value must not be blank.
- It does not silently trim, uppercase, alias, or otherwise normalize configured values.
- The set of currently supported Suppliers is determined by application configuration and Composition Root registration, not by the `SupplierId` type itself.
- Adding Supplier C does not require modifying a central Supplier enum or existing Supplier-specific implementations.
- Configured Supplier capabilities are validated during application bootstrap.
- For every configured Supplier in the current implementation, both a Catalog Port and an Availability Port implementation must be registered.
- Missing or inconsistent Supplier wiring fails fast before Catalog bootstrap or customer Search execution.
- Persistence stores `SupplierId` as `VARCHAR`.
- Public API representations expose the Supplier identifier as a plain string rather than a value-object wrapper.

Rationale:

This preserves type safety between Supplier identity and other string-valued identifiers while keeping the
supported Supplier set open to adapter registration.

---

# 7. Unified Search Model

## SEA-001 Unified StayOffer

**Class:** `REQUIRED`

Supplier results must be converted into one consistent internal accommodation-offer model.

Acceptance:

- Supplier A and B produce the same internal offer shape.
- Customer API response structure does not change by Supplier.
- Supplier-specific DTOs are not exposed externally.

---

## SEA-002 RoomType and StayOffer separation

**Class:** `PROJECT-MUST`

`RoomType` describes the room category.  
`StayOffer` describes a sellable result for a specific search.

Acceptance:

RoomType owns stable characteristics such as:

- room type name,
- maximum occupancy.

StayOffer owns search-dependent characteristics such as:

- price,
- availability,
- breakfast condition,
- Supplier source.

---

## SEA-003 Stay price normalization

**Class:** `PROJECT-DECISION`

The common price representation is:

> Total customer price for the requested stay period, with currency.

Acceptance:

- Supplier A total is calculated from all nightly price and tax components.
- Supplier B's provided stay total is used directly.
- Currency is preserved.
- Missing daily prices are never inferred.
- Missing tax amounts are never inferred.

---

## SEA-004 Currency preservation

**Class:** `REQUIRED`

Acceptance:

- Currency accompanies monetary values.
- Supplier currency is preserved.
- Currency conversion is not performed implicitly.

---

## DEC-SEA-002 Unified information-retention policy

**Class:** `PROJECT-DECISION`

Supplier information is handled using four explicit policies:

```text
PRESERVE
DERIVE / NORMALIZE
VALIDATE THEN DROP
DISCARD
```

Rules:

- Stable identity/mapping and Catalog-owned metadata required by the system are preserved.
- Supplier-specific price representations are normalized into:
  `whole-stay customer-facing total + currency`.
- Supplier A nightly price/tax rows are derivation inputs and are not retained in the common Search model.
- Supplier B `taxIncluded` validates compatible gross-total semantics and is dropped after validation.
- Supplier B nightly price/tax values are never invented.
- Daily inventory is normalized transiently, validated for exact stay-date coverage, and reduced to whole-stay availability.
- `breakfastIncluded` is preserved as a Search-dependent commercial condition.
- Availability-response names and occupancy are not authoritative; Catalog remains the metadata authority.
- External Supplier Property/Room codes are retained internally only for identity mapping/joining and are not exposed in the public Search API.
- Raw Supplier failures are normalized into internal failure semantics and reduced to public degraded/unavailable facts.

The unified model intentionally prefers the smallest common semantics that every supported Supplier can
truthfully provide over a richer superset filled with nulls or fabricated values.

---

## SEA-005 Offer conditions

**Class:** `PROJECT-MUST`

Acceptance:

- `breakfastIncluded` is preserved.
- Breakfast inclusion belongs to `StayOffer`, not `RoomType`.
- Search API consumers can distinguish offers with different breakfast conditions.

---

# 8. Search API

## SEA-006 Search endpoint

**Class:** `REQUIRED`

Endpoint:

`GET /api/v1/stays/search`

Required inputs:

- `checkIn`
- `checkOut`
- `adults`
- `children`

---

## SEA-007 Stay-period semantics

**Class:** `REQUIRED`

Acceptance:

- `checkOut` must be after `checkIn`.
- Check-out day is excluded from stay dates.
- `2026-09-01 → 2026-09-04` represents exactly three nights.

---

## SEA-008 Occupancy semantics

**Class:** `REQUIRED`

Acceptance:

- Adults and children remain distinguishable.
- `maxOccupancy` represents total supported occupants for one room.
- A Supplier response that contradicts the requested occupancy may not become a valid offer.

---

## SEA-009 Search target

**Class:** `REQUIRED`

Acceptance:

- Search covers all currently owned/searchable Supplier Properties.
- Search targets come from persisted Catalog mappings.
- Search does not require region or keyword filters.

---

## SEA-010 Supplier request batching

**Class:** `REQUIRED`

Acceptance:

- A Supplier request never contains more than 50 Property codes.
- More than 50 Properties are divided into multiple batches.
- Results from all successful batches are merged.

---

## SEA-011 Bounded batch concurrency

**Class:** `PROJECT-MUST`

Acceptance:

- Batch requests are not launched with unbounded concurrency.
- Each Supplier has an explicit concurrency upper bound.
- The bound is independently configurable per Supplier.
- The bound applies across concurrent customer Search requests within one application instance, not only inside one Search invocation.
- Supplier adapters use one shared limiter per Supplier adapter instance; do not create a new concurrency limiter for every Search request.
- The initial default is `5`.
- `5` is a conservative initial tuning default, not a business invariant, Supplier protocol constant, benchmark-derived optimum, or production SLA.
- With the current 50-Property request limit, concurrency `5` allows at most five simultaneous batch requests for one Supplier, representing at most 250 Property targets in active outbound calls at one time.
- Production tuning should use observed Supplier request latency, timeout, rate-limit, and failure metrics.
- The bound limits fan-out but does not itself guarantee a fixed end-to-end Supplier execution latency when many batches are queued.

---

## SEA-012 Parallel Supplier search

**Class:** `REQUIRED`

Acceptance:

- Supplier A and Supplier B can be queried concurrently.
- Waiting for one Supplier is not a prerequisite for starting another.
- One Supplier's latency does not delay another Supplier's request initiation.

---

# 9. Availability

## SEA-013 N-night availability

**Class:** `REQUIRED`

For a stay covering multiple nights:

`availableRooms = minimum(remainingRooms for every stay date)`

Acceptance:

- Every stay date is considered.
- If any stay date has zero inventory, `availableRooms = 0`.

---

## SEA-014 Availability completeness

**Class:** `PROJECT-MUST`

A valid offer must have a coherent inventory series.

Reject the item as invalid when relevant inventory contains:

- a missing stay date,
- a duplicate stay date,
- an unexpected date outside the requested period,
- a negative inventory value.

Invalid inventory must never be normalized into a valid availability result.

---

## SEA-015 Zero-inventory exposure

**Class:** `PROJECT-DECISION`

Offers with `availableRooms = 0` remain in the search response.

Acceptance:

- Zero availability is not silently filtered out.
- `availableRooms = 0` means the offer exists but cannot be booked for the requested stay.
- This choice and rationale are documented.

---

# 10. Search Response

## SEA-016 Minimum Property information

**Class:** `REQUIRED`

Every offer includes:

- internal Property ID,
- Property name.

---

## SEA-017 Minimum RoomType information

**Class:** `REQUIRED`

Every offer includes:

- internal RoomType ID,
- RoomType name,
- maximum occupancy.

---

## SEA-018 Minimum commercial information

**Class:** `REQUIRED`

Every offer includes:

- availability for the full stay,
- Supplier source,
- normalized price,
- currency.

---

## SEA-019 Partial-failure visibility

**Class:** `REQUIRED`

Acceptance:

- Successful results remain available when another Supplier fails.
- The response indicates that a Supplier failure occurred.
- Raw Supplier protocol errors are not exposed as the public API contract.

---

# 11. Resilience

## DEC-RES-001 Supplier execution latency budget

**Class:** `PROJECT-DECISION`

The current implementation combines per-request timeouts with bounded Supplier-global batch concurrency.

Acceptance:

- Connection/response timeout applies to one outbound Supplier HTTP request, not to the complete multi-batch Supplier execution.
- When batch count exceeds the concurrency bound, multiple request waves may execute.
- The current implementation therefore does not claim a constant end-to-end latency bound for arbitrarily large Supplier target sets.
- Increasing concurrency alone is not treated as the solution to total execution-latency growth.
- A production evolution may introduce a Supplier-level execution deadline/budget.
- Such a future deadline may cancel unfinished or not-yet-started batch work while preserving already completed batch results.

---

## RES-001 Connection timeout

**Class:** `REQUIRED`

Acceptance:

- Supplier connection timeout is explicitly configured.
- It is configurable independently from response timeout.
- Its selected default and rationale are documented.

---

## RES-002 Response timeout

**Class:** `REQUIRED`

Acceptance:

- Supplier response timeout is explicitly configured.
- A connected but non-responsive Supplier eventually fails.
- Timeout does not indefinitely block the integrated search.
- The Mock no-response mode verifies the behavior.

---

## RES-003 Supplier-level partial failure

**Class:** `REQUIRED`

Acceptance:

- Supplier A failure does not discard Supplier B success.
- Supplier B failure does not discard Supplier A success.
- Failure information is included in the integrated result.

---

## RES-004 Batch-level partial failure

**Class:** `PROJECT-MUST`

Acceptance:

- A failed batch does not discard successful batches from the same Supplier.
- Successful batch results remain eligible for the final response.
- Batch implementation details are not exposed as public API concepts.

---

## RES-005 Invalid-response isolation

**Class:** `PROJECT-DECISION`

Response-level failure:

- malformed/unusable response envelope,
- failed Supplier result status,
- response that cannot be interpreted safely.

Result: the affected batch fails.

Item-level failure:

- malformed item,
- missing/duplicate stay dates,
- invalid amount or inventory,
- unknown mapping,
- impossible occupancy,
- otherwise non-normalizable offer data.

Result: the invalid item is excluded while valid sibling items remain usable.

If no valid item remains, the batch is treated as `INVALID_RESPONSE`.

Normalization failures must be observable through logs and/or metrics.

---

## RES-006 Search-unavailable semantics

**Class:** `PROJECT-DECISION`

- Established Catalog baseline + all relevant Suppliers succeed but return no offers → `200 OK`, empty offers.
- Established Catalog baseline + at least one relevant Supplier succeeds → `200 OK`, successful offers plus failure summary.
- Established Catalog baseline + every queried Supplier fails → `503 Service Unavailable`.
- Catalog bootstrap gate closed because any configured Supplier lacks a usable Catalog baseline → `503 Service Unavailable`; normal Search execution does not begin.

This distinguishes:

```text
known empty Catalog / no available product
≠
Catalog state unknown / search could not be completed
```

---

# 12. Catalog Synchronization Decisions

## DEC-CAT-001 Startup synchronization and Catalog baseline

**Class:** `PROJECT-DECISION`

Catalog synchronization runs once during application startup.

A **usable Catalog baseline** means the application has enough established Catalog state for one configured
Supplier to distinguish a known Catalog from an unknown Catalog.

For the current startup:

```text
current startup sync succeeds
→ usable baseline

current startup sync fails + previously persisted Catalog state exists
→ usable stale baseline

current startup sync fails + no previously persisted Catalog state exists
→ no usable baseline
```

Acceptance:

- Each configured Supplier synchronization is attempted independently.
- One Supplier failure does not discard another Supplier's successful sync.
- Existing valid mappings remain unchanged when a Supplier sync fails.
- A successful synchronization establishes a usable baseline even when the valid Supplier snapshot is empty.
- Previously persisted Catalog state may act as a stale baseline when the current synchronization fails.
- Search traffic is accepted only when every configured Supplier has a usable Catalog baseline.
- If any configured Supplier has no usable baseline, Search traffic is rejected before normal Search execution.
- `known Catalog with zero searchable Properties` is a valid empty Catalog and is not equivalent to `Catalog baseline unavailable`.
- Search readiness is an application/operational concern; it does not add a state to `SearchOutcome`.
- An external administrator synchronization API is not required.
- A future scheduler or synchronization trigger may refresh stale baselines and reopen the Search traffic gate.

Current limitation:

- Baseline persistence is inferred from existing Catalog state rather than a separate synchronization-metadata table.
- Therefore, a Supplier whose historically successful Catalog has always been truly empty cannot be distinguished
  across a later process restart from a Supplier that has never established a baseline if the new sync also fails.
  This edge case is accepted for the current implementation scope.

---

## DEC-CAT-002 Snapshot reconciliation

**Class:** `PROJECT-DECISION`

A successfully validated Supplier catalog is treated as a complete snapshot.

Reconciliation:

- existing identity → preserve internal ID and update metadata,
- new identity → create internal ID,
- previously known but missing identity → mark inactive,
- inactive identity that reappears → reactivate with the same internal ID.

Physical deletion is not used for normal reconciliation.

Acceptance:

- An incomplete or invalid catalog snapshot is never partially applied.
- Only active mappings participate in search.
- Stable internal identity is preserved through deactivate/reactivate cycles.

---

## DEC-CAT-003 Catalog transaction boundary

**Class:** `PROJECT-DECISION`

Supplier HTTP communication is outside the database transaction.

Flow:

`fetch → validate → normalize → transactional snapshot reconciliation`

Acceptance:

- External HTTP waits do not hold DB transactions open.
- One Supplier snapshot is applied atomically.
- Failure during snapshot persistence rolls back that Supplier's update.
- Supplier A and Supplier B reconciliation transactions are independent.

---

# 13. Cross-Supplier Identity Decision

## DEC-SEA-001 No automatic canonical merge

**Class:** `PROJECT-DECISION`

Different Suppliers may represent the same real-world Property or RoomType with different internal IDs.

Acceptance:

- No name-based or price-based automatic merge is performed.
- Cross-Supplier canonicalization is not required by the core search flow.
- A future canonicalization capability must not be assumed by current identity rules.

---

# 14. Mock Supplier

## MCK-001 Required scenarios

**Class:** `REQUIRED`

The Mock environment must reproduce:

- normal responses,
- Supplier failure responses,
- connected-but-no-response behavior.

---

## MCK-002 Supplier-specific failure behavior

**Class:** `REQUIRED`

Acceptance:

- Supplier A can reproduce transport-level failure.
- Supplier B can reproduce body-level failure while HTTP transport succeeds.
- Both can return normal search data.

---

## MCK-003 Independent runtime

**Class:** `REQUIRED`

Acceptance:

- Mock Supplier and application run on different server ports/processes.
- They can be started independently.
- Mock implementation quality beyond required scenarios is not a project priority.

---

# 15. Documentation

## DOC-001 README

**Class:** `REQUIRED`

README must include:

- build instructions,
- run instructions,
- major design decisions and rationale,
- links to detailed project documentation.

---

## DOC-002 Unified-model rationale

**Class:** `REQUIRED`

Document:

- what was chosen as the internal standard,
- what Supplier information is preserved,
- what information is derived/normalized,
- what information is used only for validation and then discarded,
- what information is intentionally discarded,
- why each information loss is acceptable.

The rationale must distinguish these information policies:

```text
PRESERVE
DERIVE / NORMALIZE
VALIDATE THEN DROP
DISCARD
```

Acceptance:

- The common model is based on semantics required by integrated Search, not on the richest Supplier payload.
- Supplier A nightly price/tax detail is not required in the common Search contract after a correct whole-stay total is derived.
- Supplier B nightly/tax detail is never fabricated.
- Daily inventory may be retained transiently for complete-stay validation/derivation, while the public contract exposes the derived whole-stay availability.
- Availability-response names and occupancy never override Catalog-owned metadata.
- Raw Supplier protocol/envelope/error details never become public Search contract fields.

---

## DOC-003 Catalog-sync rationale

**Class:** `REQUIRED`

Document:

- when catalog synchronization occurs,
- why that strategy was chosen,
- how it could evolve in a production environment.

---

## DOC-004 Zero-inventory rationale

**Class:** `REQUIRED`

Document why zero-inventory offers are returned rather than removed.

---

## DOC-005 Timeout rationale

**Class:** `REQUIRED`

Document:

- connection-timeout default,
- response-timeout default,
- why both exist,
- why the selected values are appropriate.

Exact values are configuration/design defaults, not requirements.

---

## DOC-006 Supplier-extension rationale

**Class:** `REQUIRED`

Document:

- what is added for a new Supplier,
- which existing areas should remain unchanged,
- how Supplier-specific protocols remain isolated.

---

## DOC-007 HTTP programming-model rationale

**Class:** `REQUIRED`

Document the choice of:

`Spring MVC + Kotlin Coroutine + WebClient`

and explain why full application-wide WebFlux is unnecessary for this implementation.

---

## DOC-008 Domain scope

**Class:** `REQUIRED`

The design must discuss the broader **relevant accommodation-integration domain** even when only the core flow is
implemented.

Document:

- current core business contexts and implemented concepts,
- adjacent domain capabilities that are relevant but not currently modeled,
- technical/integration evolution areas separately from business-domain capabilities,
- explicitly unmodeled product concepts,
- explicit out-of-scope features,
- implementation boundaries,
- reasons for the selected scope,
- concrete requirement/boundary triggers that would justify revisiting an excluded concept.

Acceptance:

- "broader domain" does not mean modeling the entire accommodation platform.
- Future capabilities are not presented as already-decided Bounded Contexts or Aggregates.
- Optional future capabilities do not appear as current Gradle modules or runtime dependencies.
- Reservation/Booking evolution is distinguished from payment integration, which remains explicitly out of scope.
- Retry, circuit breaker, cache, and normalization quarantine are treated as integration/infrastructure evolution unless a concrete business policy requires otherwise.
- Physical Room and persistent RatePlan are not introduced without a stable identity/lifecycle requirement.

---

## DOC-009 Architecture documentation

**Class:** `PROJECT-MUST`

Architecture documentation must define:

- module boundaries,
- dependency rules,
- domain/application/port/adapter responsibilities,
- integration boundaries,
- transaction boundaries.

Documentation and actual Gradle dependencies must not contradict each other.

---

## DOC-010 API documentation

**Class:** `PROJECT-MUST`

Search API must be discoverable through generated OpenAPI/Swagger documentation.

---

# 16. Verification

## TST-001 Domain tests

**Class:** `PROJECT-MUST`

Core domain invariants require automated tests, including:

- stay-period validation,
- N-night availability,
- price normalization,
- invalid inventory handling,
- identity stability where applicable.

Domain tests should not require HTTP or database infrastructure unless the behavior inherently depends on them.

---

## TST-002 Core integration scenarios

**Class:** `PROJECT-MUST`

At minimum verify:

### Catalog
- initial synchronization,
- repeated synchronization preserves IDs,
- deactivate/reactivate preserves IDs,
- failed catalog sync preserves previous valid state.

### Search
- Supplier A success + Supplier B success,
- A failure + B success,
- A timeout + B success,
- A success + B logical failure,
- all Suppliers fail,
- zero inventory,
- more than 50 Properties,
- one malformed item alongside valid items.

### End-to-end
- catalog sync → persisted mapping → integrated search.

---

# 17. Observability

## OBS-001 Supplier integration metrics

**Class:** `PROJECT-MUST`

The system must make Supplier integration health observable.

At minimum support measuring:

- request latency,
- success/failure outcome,
- timeout frequency.

Requirements:

- Supplier is a valid low-cardinality dimension.
- Failure category may be used as a low-cardinality dimension.
- Property/Room IDs must not be metric labels.

---

# 18. AI Usage & Progress Record

## AI-001 AI usage record

**Class:** `PROJECT-MUST`

Maintain a root-level `AI_USAGE.md` for significant AI-assisted engineering work.

Record, when applicable:

- what was asked at a useful summary level,
- significant AI suggestions,
- what was accepted,
- what was modified,
- what was rejected or deferred,
- why the final human decision was made,
- how the final result was or will be verified.

Rules:

- A raw prompt transcript is not sufficient.
- Do not copy restricted source/reference text into AI usage records.
- Do not record trivial autocomplete, formatting, boilerplate, or minor naming assistance.
- Do not invent rejected suggestions, failures, or implementation history retrospectively.
- AI usage records are process evidence, not normative architecture.

---

## AI-002 Progress journal

**Class:** `PROJECT-MUST`

Maintain root-level `JOURNAL.md` as a selective engineering-decision/problem history.

Record meaningful events such as:

- major architectural/domain decisions,
- rejected alternatives,
- implementation blockers and root-cause analysis,
- important transaction/concurrency/performance/resilience findings,
- verification results that changed implementation or design,
- meaningful scope trade-offs.

Do not use `JOURNAL.md` as a daily task list, commit log, or duplicate AI transcript.

Historical records are not normative architecture.

Process-document separation:

```text
Canonical docs → current system truth
JOURNAL.md     → engineering decision/problem history
AI_USAGE.md    → AI contribution + human judgment history
Git history    → exact code/change history
```

---

# 19. Explicit Out of Scope

The following are not required by the core project and must not become dependencies of the core search flow.

**Class:** `OUT-OF-SCOPE`

- end-user authentication / authorization,
- payment integration,
- administrator product features,
- frontend implementation,
- real commercial Supplier integration,
- region search,
- keyword search,
- sorting,
- pagination.

---

# 20. Optional Enhancements

These may be considered only after all core requirements are complete.

**Class:** `OPTIONAL`

- retry policy and backoff,
- circuit breaker,
- price/inventory cache,
- persistent quarantine of normalization failures,
- cross-Supplier duplicate/canonical Property merging,
- currency conversion,
- reservation creation/cancellation and compensation flow.

Optional work must not destabilize required behavior.

---

# 21. Requirement Change Policy

This document is considered **frozen for core implementation**.

A requirement may be changed only when:

1. the current requirement is shown to be incorrect, contradictory, or insufficient;
2. the impact on existing behavior is identified;
3. the alternative is documented;
4. a design decision/ADR records the change when material;
5. affected tests and documentation are updated.

Implementation convenience alone is not sufficient reason to change a requirement.