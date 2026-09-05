# VERIFICATION.md

> Canonical verification and acceptance specification.
>
> - Requirements: `REQUIREMENTS.md`
> - Domain rules: `DOMAIN.md`
> - Application flows: `USE_CASES.md`
> - Supplier semantics: `INTEGRATION.md`
> - Runtime boundaries: `ARCHITECTURE.md`
> - Persistence: `PERSISTENCE.md`
> - Public HTTP contract: `API.md`
>
> Defines **how mandatory requirements and project decisions are proven complete**.
> This is the canonical traceability layer between requirements and implementation evidence.
>
> The goal is not maximum test count. Verify each rule at the lowest layer that owns it; use higher-level tests to prove collaboration and wiring.

---

## 1. Verification Principles

### 1.1 Traceability

```text
REQUIRED
→ at least one verification entry

PROJECT-MUST
→ at least one verification entry

PROJECT-DECISION
→ verify when correctness, contract stability, or architecture depends on it

OPTIONAL
→ verify only if implemented
```

One requirement may map to multiple scenarios when one test cannot prove all relevant behavior.

### 1.2 Risk Ownership

```text
Domain rule
→ Domain Unit

Application decision
→ Application Unit

Database behavior
→ Persistence Integration

Supplier protocol
→ Supplier Adapter Integration

HTTP contract
→ Web Contract

Cross-module collaboration
→ Cross-module / E2E

Repository/document/configuration requirement
→ Static / Build / Manual verification
```

Do not repeat complete edge-case suites at every layer.

### 1.3 Deterministic Tests

Prefer:

```text
barriers
latches
controlled fake Ports
bounded in-flight counters
short test-only timeout configuration
```

Avoid brittle verification based mainly on:

```text
arbitrary elapsed-time thresholds
Thread.sleep synchronization
production timeout values in every test
```

---

# 2. Verification Methods

| Method | Purpose |
|---|---|
| `DOMAIN_UNIT` | Pure invariant/calculation behavior |
| `INFRA_UNIT` | Domain-neutral technical mechanism behavior |
| `APPLICATION_UNIT` | Use-case orchestration with fake Ports |
| `PERSISTENCE_INTEGRATION` | JPA/PostgreSQL behavior using Testcontainers |
| `SUPPLIER_INTEGRATION` | HTTP protocol, DTO translation, batching, timeout, failure mapping |
| `WEB_CONTRACT` | MVC request/response/status serialization |
| `CROSS_MODULE` | Real application transaction/wiring across modules |
| `E2E` | Main app + PostgreSQL + real Mock Supplier process |
| `ARCH_STATIC` | Module/package dependency enforcement |
| `BUILD_CHECK` | Build/runtime artifact verification |
| `DOC_REVIEW` | Required documentation verification |
| `REPO_REVIEW` | Public repository safety/submission checks |
| `COMPONENT_TEST` | Standalone component behavior such as Mock Supplier modes |
| `MANUAL_SMOKE` | Small runtime checks not worth deeper automation |

---

# 3. Priority

| Priority | Meaning |
|---|---|
| `P0` | Mandatory acceptance/core correctness |
| `P1` | Strong confidence for important edge cases |
| `P2` | Optional/implemented-extra verification |

Rules:

```text
REQUIRED verification → P0
PROJECT-MUST verification → P0
optional resilience feature → P2 unless explicitly promoted
```

---

# 4. Status

| Status | Meaning |
|---|---|
| `PLANNED` | Scenario defined; evidence not yet passing |
| `PASSING` | Automated verification implemented and passing |
| `MANUAL-PASS` | Static/manual/document verification completed |

Do not use percentage progress.

Release readiness requires every mandatory P0 scenario to be `PASSING` or `MANUAL-PASS`.

---

# 5. Catalog Domain Verification

| ID | Priority | Method | Scenario | Expected | Status |
|---|---|---|---|---|---|
| `V-CAT-DOM-01` | P0 | DOMAIN_UNIT | Existing RoomType reconciled | Existing `RoomTypeId` preserved | PASSING |
| `V-CAT-DOM-02` | P0 | DOMAIN_UNIT | New RoomType appears | New ACTIVE RoomType added | PASSING |
| `V-CAT-DOM-03` | P0 | DOMAIN_UNIT | Existing RoomType missing | RoomType becomes INACTIVE | PASSING |
| `V-CAT-DOM-04` | P0 | DOMAIN_UNIT | INACTIVE RoomType reappears | Same ID becomes ACTIVE | PASSING |
| `V-CAT-DOM-05` | P0 | DOMAIN_UNIT | Duplicate SupplierRoomTypeCode in one Property | Reject invalid Aggregate state | PASSING |
| `V-CAT-DOM-06` | P0 | DOMAIN_UNIT | Property external identity reused | Same `PropertyId` retained | PASSING |
| `V-CAT-DOM-07` | P0 | DOMAIN_UNIT | INACTIVE Property reappears | Same `PropertyId` becomes ACTIVE | PASSING |

---

# 6. Catalog Application Verification

Target:

```text
UC-CAT-01 SynchronizeSupplierCatalog
```

Use fake:

```text
SupplierCatalogPort
PropertyRepository
PropertyIdGenerator
RoomTypeIdGenerator
```

| ID | Priority | Method | Scenario | Expected | Status |
|---|---|---|---|---|---|
| `V-CAT-UC-01` | P0 | APPLICATION_UNIT | First sync into empty Catalog | New stable IDs created; ACTIVE state persisted | PASSING |
| `V-CAT-UC-02` | P0 | APPLICATION_UNIT | Same complete snapshot synced again | Existing IDs preserved | PASSING |
| `V-CAT-UC-03` | P0 | APPLICATION_UNIT | Existing Property absent from valid complete snapshot | Property becomes INACTIVE | PASSING |
| `V-CAT-UC-04` | P0 | APPLICATION_UNIT | INACTIVE Property returns | Same ID reactivated | PASSING |
| `V-CAT-UC-05` | P0 | APPLICATION_UNIT | Supplier Catalog fetch fails | State unchanged; no absence inference | PASSING |
| `V-CAT-UC-06` | P0 | APPLICATION_UNIT | Snapshot validation fails | No reconciliation/persistence | PASSING |
| `V-CAT-UC-07` | P0 | APPLICATION_UNIT | Valid complete empty snapshot | All existing Supplier Properties become INACTIVE | PASSING |
| `V-CAT-UC-08` | P1 | APPLICATION_UNIT | One Supplier sync fails independently | No cross-Supplier rollback implication | PASSING |

---

# 7. Catalog Published Read Verification

Target:

```text
Q-CAT-01 ReadSearchableCatalog
```

| ID | Priority | Method | Scenario | Expected | Status |
|---|---|---|---|---|---|
| `V-CAT-READ-01` | P0 | APPLICATION_UNIT | ACTIVE Property + ACTIVE RoomType | Published projection returned | PASSING |
| `V-CAT-READ-02` | P0 | APPLICATION_UNIT | INACTIVE Property | Excluded | PASSING |
| `V-CAT-READ-03` | P0 | APPLICATION_UNIT | ACTIVE Property + INACTIVE RoomType | RoomType excluded | PASSING |
| `V-CAT-READ-04` | P0 | APPLICATION_UNIT | Property has no ACTIVE RoomType | Property need not be returned | PASSING |
| `V-CAT-READ-05` | P0 | APPLICATION_UNIT | Projection returned | No Aggregate/JPA type exposed | PASSING |

---

# 8. Search Domain Verification

## 8.1 StayPeriod

| ID | Priority | Method | Scenario | Expected | Status |
|---|---|---|---|---|---|
| `V-SEA-PERIOD-01` | P0 | DOMAIN_UNIT | `checkIn < checkOut` | Valid | PLANNED |
| `V-SEA-PERIOD-02` | P0 | DOMAIN_UNIT | `checkIn == checkOut` | Reject | PLANNED |
| `V-SEA-PERIOD-03` | P0 | DOMAIN_UNIT | `checkIn > checkOut` | Reject | PLANNED |
| `V-SEA-PERIOD-04` | P0 | DOMAIN_UNIT | `09-01 → 09-04` | Required dates `01,02,03`; checkout excluded | PLANNED |

## 8.2 GuestComposition

| ID | Priority | Method | Scenario | Expected | Status |
|---|---|---|---|---|---|
| `V-SEA-GUEST-01` | P0 | DOMAIN_UNIT | Valid adults/children | Values preserved separately | PLANNED |
| `V-SEA-GUEST-02` | P0 | DOMAIN_UNIT | Invalid negative population | Reject according to input/domain policy | PLANNED |

## 8.3 StayAvailability

| ID | Priority | Method | Scenario | Expected | Status |
|---|---|---|---|---|---|
| `V-SEA-AVAIL-01` | P0 | DOMAIN_UNIT | Inventory `[3,1,5]` | `availableRooms=1` | PLANNED |
| `V-SEA-AVAIL-02` | P0 | DOMAIN_UNIT | Inventory `[3,0,5]` | `availableRooms=0` | PLANNED |
| `V-SEA-AVAIL-03` | P0 | DOMAIN_UNIT | Required date missing | Invalid | PLANNED |
| `V-SEA-AVAIL-04` | P0 | DOMAIN_UNIT | Duplicate date | Invalid | PLANNED |
| `V-SEA-AVAIL-05` | P0 | DOMAIN_UNIT | Out-of-period date | Invalid | PLANNED |
| `V-SEA-AVAIL-06` | P0 | DOMAIN_UNIT | Negative inventory | Invalid | PLANNED |

## 8.4 Money / StayPrice

| ID | Priority | Method | Scenario | Expected | Status |
|---|---|---|---|---|---|
| `V-SEA-PRICE-01` | P0 | DOMAIN_UNIT | Amount + currency constructed | Both preserved | PLANNED |
| `V-SEA-PRICE-02` | P0 | DOMAIN_UNIT | Different currencies | No implicit FX conversion | PLANNED |

---

# 9. Search Application Verification

Target:

```text
UC-SEA-01 SearchStays
```

Use fake:

```text
ReadSearchableCatalog
SupplierAvailabilityPort A
SupplierAvailabilityPort B
```

| ID | Priority | Method | Scenario | Expected | Status |
|---|---|---|---|---|---|
| `V-SEA-UC-01` | P0 | APPLICATION_UNIT | A success + B success | `Result`, no failures | PLANNED |
| `V-SEA-UC-02` | P0 | APPLICATION_UNIT | A success + B failure | A Offers preserved; partial Result | PLANNED |
| `V-SEA-UC-03` | P0 | APPLICATION_UNIT | A failure + B success | B Offers preserved; partial Result | PLANNED |
| `V-SEA-UC-04` | P0 | APPLICATION_UNIT | A failure + B failure | `Unavailable` | PLANNED |
| `V-SEA-UC-05` | P0 | APPLICATION_UNIT | No searchable Catalog target | `Result([],[])`; Supplier Ports not invoked | PLANNED |
| `V-SEA-UC-06` | P0 | APPLICATION_UNIT | A legitimate empty + B failure | Partial `Result([], failures)` | PLANNED |
| `V-SEA-UC-07` | P0 | APPLICATION_UNIT | Unknown Supplier RoomType code | Invalid item dropped; valid siblings preserved | PLANNED |
| `V-SEA-UC-08` | P0 | APPLICATION_UNIT | Guest count exceeds MaxOccupancy | Offer not produced | PLANNED |
| `V-SEA-UC-09` | P0 | APPLICATION_UNIT | Valid item with zero full-stay availability | StayOffer retained with `availableRooms=0` | PLANNED |
| `V-SEA-UC-10` | P0 | APPLICATION_UNIT | Availability metadata conflicts with Catalog | Catalog metadata remains authoritative | PLANNED |

---

# 10. Supplier-Level Parallelism Verification

Do not prove concurrency only through elapsed-time assertions.

Recommended controlled fake:

```text
A starts ──┐
           ├─ release only after both started
B starts ──┘
```

| ID | Priority | Method | Scenario | Expected | Status |
|---|---|---|---|---|---|
| `V-SEA-CON-01` | P0 | APPLICATION_UNIT | Search has A and B targets | Both Supplier executions enter before either is released | PLANNED |
| `V-SEA-CON-02` | P0 | APPLICATION_UNIT | A fails while B executes | Failure does not cancel/discard B | PLANNED |

---

# 11. Supplier A Integration Verification

Use an in-process controllable HTTP stub for focused adapter tests.

## Request translation

| ID | Priority | Method | Scenario | Expected | Status |
|---|---|---|---|---|---|
| `V-INT-A-REQ-01` | P0 | SUPPLIER_INTEGRATION | Availability request | `X-Api-Key` included | PLANNED |
| `V-INT-A-REQ-02` | P0 | SUPPLIER_INTEGRATION | Dates/guests supplied | Values preserved | PLANNED |
| `V-INT-A-REQ-03` | P0 | SUPPLIER_INTEGRATION | External targets supplied | Correct Supplier Property codes transmitted | PLANNED |

## Catalog

| ID | Priority | Method | Scenario | Expected | Status |
|---|---|---|---|---|---|
| `V-INT-A-CAT-01` | P0 | SUPPLIER_INTEGRATION | Normal `/a/v1/hotels` response | Neutral complete Catalog snapshot | PLANNED |
| `V-INT-A-CAT-02` | P0 | SUPPLIER_INTEGRATION | Structurally invalid Catalog | Fail closed; no partial snapshot | PLANNED |

## Availability / Price

| ID | Priority | Method | Scenario | Expected | Status |
|---|---|---|---|---|---|
| `V-INT-A-PRICE-01` | P0 | SUPPLIER_INTEGRATION | Complete daily rate rows | `Σ(nightlyRate + taxAmount)` whole-stay price | PLANNED |
| `V-INT-A-PRICE-02` | P0 | SUPPLIER_INTEGRATION | Required price date missing | `INVALID_RESPONSE` | PLANNED |
| `V-INT-A-PRICE-03` | P0 | SUPPLIER_INTEGRATION | Duplicate price date | `INVALID_RESPONSE` | PLANNED |
| `V-INT-A-PRICE-04` | P0 | SUPPLIER_INTEGRATION | Out-of-period price row | `INVALID_RESPONSE` | PLANNED |

## HTTP failure normalization

Parameterized verification ID:

```text
V-INT-A-ERR-01
```

| Supplier A status | Expected internal failure |
|---|---|
| `400` | `INVALID_REQUEST` |
| `401` | `AUTHENTICATION_FAILED` |
| `429` | `RATE_LIMITED` |
| `500` | `SUPPLIER_ERROR` |
| `503` | `SERVICE_UNAVAILABLE` |

Priority/method:

```text
P0 / SUPPLIER_INTEGRATION
```

---

# 12. Supplier B Integration Verification

## Request translation

| ID | Priority | Method | Scenario | Expected | Status |
|---|---|---|---|---|---|
| `V-INT-B-REQ-01` | P0 | SUPPLIER_INTEGRATION | Supplier B request | `X-Api-Key` included | PLANNED |
| `V-INT-B-REQ-02` | P0 | SUPPLIER_INTEGRATION | Dates/guests supplied | Values preserved | PLANNED |
| `V-INT-B-REQ-03` | P0 | SUPPLIER_INTEGRATION | Targets supplied | Correct `propertyIds` transmitted | PLANNED |

## Protocol resultCode

| ID | Priority | Method | Scenario | Expected | Status |
|---|---|---|---|---|---|
| `V-INT-B-PROTO-01` | P0 | SUPPLIER_INTEGRATION | HTTP 200 + `0000` | Success | PLANNED |
| `V-INT-B-PROTO-02` | P0 | SUPPLIER_INTEGRATION | HTTP 200 + `E400` | `INVALID_REQUEST` | PLANNED |
| `V-INT-B-PROTO-03` | P0 | SUPPLIER_INTEGRATION | HTTP 200 + `E401` | `AUTHENTICATION_FAILED` | PLANNED |
| `V-INT-B-PROTO-04` | P0 | SUPPLIER_INTEGRATION | HTTP 200 + `E429` | `RATE_LIMITED` | PLANNED |
| `V-INT-B-PROTO-05` | P0 | SUPPLIER_INTEGRATION | HTTP 200 + `E500` | `SUPPLIER_ERROR` | PLANNED |
| `V-INT-B-PROTO-06` | P0 | SUPPLIER_INTEGRATION | HTTP 200 + `E503` | `SERVICE_UNAVAILABLE` | PLANNED |

Catalog and availability paths both respect body-level result semantics.

## Price

| ID | Priority | Method | Scenario | Expected | Status |
|---|---|---|---|---|---|
| `V-INT-B-PRICE-01` | P0 | SUPPLIER_INTEGRATION | Valid `totalPrice` | Whole-stay amount preserved unchanged | PLANNED |
| `V-INT-B-PRICE-02` | P0 | SUPPLIER_INTEGRATION | Valid B price | No fabricated nightly/tax fields enter neutral contract | PLANNED |

---

# 13. Availability Partial-Normalization Verification

| ID | Priority | Method | Scenario | Expected | Status |
|---|---|---|---|---|---|
| `V-INT-NORM-01` | P0 | SUPPLIER_INTEGRATION | One valid + one invalid item | Valid sibling preserved; failure recorded | PLANNED |
| `V-INT-NORM-02` | P0 | SUPPLIER_INTEGRATION | Non-empty payload but no item safely normalizable | Affected batch = `INVALID_RESPONSE` | PLANNED |
| `V-INT-NORM-03` | P0 | SUPPLIER_INTEGRATION | Legitimate successful empty payload | Successful empty batch | PLANNED |
| `V-INT-NORM-04` | P0 | SUPPLIER_INTEGRATION | Response/batch envelope malformed | Entire affected batch fails | PLANNED |

---

# 14. Batching Verification

Supplier request limit:

```text
max 50 Property codes/request
```

Core example:

```text
121 targets
→ 50 + 50 + 21
→ 3 requests
```

| ID | Priority | Method | Scenario | Expected | Status |
|---|---|---|---|---|---|
| `V-INT-BATCH-01` | P0 | SUPPLIER_INTEGRATION | 1–50 targets | One request | PLANNED |
| `V-INT-BATCH-02` | P0 | SUPPLIER_INTEGRATION | 51 targets | Two requests; no batch > 50 | PLANNED |
| `V-INT-BATCH-03` | P0 | SUPPLIER_INTEGRATION | 121 targets | `50,50,21` | PLANNED |
| `V-INT-BATCH-04` | P0 | SUPPLIER_INTEGRATION | Middle batch fails, others succeed | Successful items preserved | PLANNED |

If batching implementation is shared internally, exhaustive chunk tests may live once at that implementation; each Supplier adapter still retains a request-limit integration smoke test.

---

# 15. Bounded Batch Concurrency Verification

Use an upstream stub that records concurrent in-flight requests.

Example test configuration:

```text
batch concurrency = 3
batches            = 10
```

Verify:

```text
maxObservedConcurrency <= 3
maxObservedConcurrency > 1
```

| ID | Priority | Method | Scenario | Expected | Status |
|---|---|---|---|---|---|
| `V-INT-CON-01` | P0 | SUPPLIER_INTEGRATION | Many batches | Concurrent requests never exceed configured limit | PLANNED |
| `V-INT-CON-02` | P0 | SUPPLIER_INTEGRATION | Multiple batches | More than one batch may progress concurrently | PLANNED |
| `V-INT-CON-03` | P1 | SUPPLIER_INTEGRATION | A/B configured differently | Each Supplier respects its own limit | PLANNED |
| `V-INT-CON-04` | P0 | SUPPLIER_INTEGRATION | Concurrent customer Searches target the same Supplier | Combined in-flight requests across Searches never exceed the one shared Supplier limit | PLANNED |
| `V-INT-CON-05` | P1 | BUILD_CHECK / APPLICATION_UNIT | No explicit concurrency override | Supplier batch concurrency binds to default `5` | PLANNED |

Do not assert exact coroutine scheduling order.

The verification must distinguish a Supplier-global limiter from a request-local limiter. A design where each
Search creates its own semaphore is invalid even if every individual Search stays below the configured limit.

---

## 15.1 Supplier Execution Latency Semantics

Per-request timeout and complete Supplier execution latency must remain distinct.

| ID | Priority | Method | Scenario | Expected | Status |
|---|---|---|---|---|---|
| `V-INT-LAT-01` | P0 | SUPPLIER_INTEGRATION / DOC_REVIEW | More batches than concurrency permits; upstream stalls | Active calls respect per-request timeout; no claim that the complete Supplier execution finishes within one timeout interval | PLANNED |
| `V-INT-LAT-02` | P1 | DOC_REVIEW | Production evolution reviewed | Supplier-level execution deadline is documented as a future option, not a current SLA | PLANNED |

---

# 16. Timeout / Connection Verification

Use shortened **test-only** timeout configuration.

Example:

```text
response timeout = 100ms
```

| ID | Priority | Method | Scenario | Expected | Status |
|---|---|---|---|---|---|
| `V-RES-TIMEOUT-01` | P0 | SUPPLIER_INTEGRATION | Connected upstream never responds | `TIMEOUT` | PLANNED |
| `V-RES-CONN-01` | P0 | SUPPLIER_INTEGRATION | Connection refused/unreachable | `CONNECTION_FAILED` | PLANNED |
| `V-RES-TIMEOUT-02` | P0 | APPLICATION_UNIT | One Supplier timeout + one succeeds | Successful Supplier result preserved | PLANNED |

---

# 17. Persistence Integration Verification

Use real:

```text
PostgreSQL Testcontainers
```

Do not substitute H2.

## Constraints

| ID | Priority | Method | Scenario | Expected | Status |
|---|---|---|---|---|---|
| `V-PER-CON-01` | P0 | PERSISTENCE_INTEGRATION | Duplicate `(supplier_id, supplier_property_code)` | DB rejects | PLANNED |
| `V-PER-CON-02` | P0 | PERSISTENCE_INTEGRATION | Duplicate `(property_id, supplier_room_type_code)` | DB rejects | PLANNED |
| `V-PER-CON-03` | P0 | PERSISTENCE_INTEGRATION | RoomType references missing Property | FK rejects | PLANNED |

## Mapping

| ID | Priority | Method | Scenario | Expected | Status |
|---|---|---|---|---|---|
| `V-PER-MAP-01` | P0 | PERSISTENCE_INTEGRATION | Domain Property with RoomTypes persisted/reloaded | Semantically equivalent Aggregate state | PASSING |
| `V-PER-MAP-02` | P0 | PERSISTENCE_INTEGRATION | Reconciliation read | ACTIVE + INACTIVE state rehydrated | PASSING |
| `V-PER-MAP-03` | P1 | PERSISTENCE_INTEGRATION | Many Properties with RoomTypes loaded | No N+1 query pattern | PLANNED |
| `V-PER-BASE-01` | P0 | PERSISTENCE_INTEGRATION | Supplier has only INACTIVE persisted Catalog rows | `hasPersistedCatalogState=true` | PASSING |
| `V-PER-BASE-02` | P0 | PERSISTENCE_INTEGRATION | Supplier has no persisted Catalog rows | `hasPersistedCatalogState=false` | PASSING |

## Search projection

| ID | Priority | Method | Scenario | Expected | Status |
|---|---|---|---|---|---|
| `V-PER-READ-01` | P0 | PERSISTENCE_INTEGRATION | ACTIVE Property + ACTIVE RoomType | Returned | PASSING |
| `V-PER-READ-02` | P0 | PERSISTENCE_INTEGRATION | INACTIVE Property | Excluded | PASSING |
| `V-PER-READ-03` | P0 | PERSISTENCE_INTEGRATION | ACTIVE Property + INACTIVE RoomType | RoomType excluded | PASSING |

---

# 18. Catalog Transaction Verification

Transaction boundary belongs to Catalog Application + real persistence adapter.

| ID | Priority | Method | Scenario | Expected | Status |
|---|---|---|---|---|---|
| `V-CAT-TX-01` | P0 | CROSS_MODULE | Multi-Property snapshot application fails before commit | Entire Supplier snapshot rolled back | PLANNED |
| `V-CAT-TX-02` | P0 | CROSS_MODULE | Supplier network call occurs | No DB transaction held during HTTP fetch | PLANNED |
| `V-CAT-TX-03` | P0 | CROSS_MODULE | A reconciliation commits; B later fails | A remains committed; B rolls back independently | PLANNED |

`V-CAT-TX-02` may use transaction instrumentation/spy or a focused integration assertion. Avoid timing-only inference.

## Catalog Bootstrap / Search Readiness

These scenarios verify the operational gate without adding a Search domain state.

| ID | Priority | Method | Scenario | Expected | Status |
|---|---|---|---|---|---|
| `V-CAT-BOOT-01` | P0 | CROSS_MODULE | Fresh DB; every configured Supplier startup sync succeeds | Every baseline available; Search gate OPEN | PLANNED |
| `V-CAT-BOOT-02` | P0 | CROSS_MODULE | Fresh DB; A sync fails, B succeeds; A has no previous state | Search gate CLOSED; normal Search not executed | PLANNED |
| `V-CAT-BOOT-03` | P0 | CROSS_MODULE | Existing A/B state; A startup sync fails, B succeeds | A stale baseline preserved; Search gate OPEN | PLANNED |
| `V-CAT-BOOT-04` | P0 | CROSS_MODULE | Valid startup sync establishes Catalog with zero searchable Properties | Baseline available; Search gate OPEN | PLANNED |
| `V-CAT-BOOT-05` | P0 | CROSS_MODULE | Gate CLOSED and Search endpoint called directly | `503 SEARCH_UNAVAILABLE`; missing targets are not treated as empty result | PLANNED |

---

# 19. Snowflake Verification

Technical generator lives in:

```text
:shared:infrastructure
```

| ID | Priority | Method | Scenario | Expected | Status |
|---|---|---|---|---|---|
| `V-ID-01` | P0 | INFRA_UNIT | Generated ID | `> 0` | PASSING |
| `V-ID-02` | P0 | INFRA_UNIT | Sequential generation | No duplicates | PASSING |
| `V-ID-03` | P0 | INFRA_UNIT | Concurrent generation | No duplicates | PASSING |
| `V-ID-04` | P0 | INFRA_UNIT | Invalid nodeId | Reject configuration | PASSING |
| `V-ID-05` | P0 | INFRA_UNIT | Clock rollback | Fail according to policy; never silently duplicate | PASSING |

Catalog tests verify semantic behavior:

```text
new identity gets an ID
same identity keeps its existing ID
```

They do not retest Snowflake bit arithmetic.

---

# 20. Web Contract Verification

Use Spring MVC slice tests or equivalent.
The real DB/Supplier clients are not required.

## Input

| ID | Priority | Method | Scenario | Expected | Status |
|---|---|---|---|---|---|
| `V-API-REQ-01` | P0 | WEB_CONTRACT | Required parameter missing | `400 INVALID_SEARCH_CONDITION` | PLANNED |
| `V-API-REQ-02` | P0 | WEB_CONTRACT | Invalid date format | canonical 400 | PLANNED |
| `V-API-REQ-03` | P0 | WEB_CONTRACT | `checkIn >= checkOut` | canonical 400 | PLANNED |
| `V-API-REQ-04` | P0 | WEB_CONTRACT | `adults < 1` | canonical 400 | PLANNED |
| `V-API-REQ-05` | P0 | WEB_CONTRACT | `children < 0` | canonical 400 | PLANNED |

## Success mapping

| ID | Priority | Method | Scenario | Expected | Status |
|---|---|---|---|---|---|
| `V-API-RES-01` | P0 | WEB_CONTRACT | `Result` without failures | `200`, `COMPLETE` | PLANNED |
| `V-API-RES-02` | P0 | WEB_CONTRACT | `Result` with failures | `200`, `PARTIAL` | PLANNED |
| `V-API-RES-03` | P0 | WEB_CONTRACT | Complete empty result | `200`, empty offers | PLANNED |
| `V-API-RES-04` | P0 | WEB_CONTRACT | Partial empty result | `200`, degraded Supplier | PLANNED |
| `V-API-RES-05` | P0 | WEB_CONTRACT | StayOffer inventory is zero | `availableRooms=0` preserved | PLANNED |
| `V-API-RES-06` | P0 | WEB_CONTRACT | Snowflake IDs returned | JSON strings | PLANNED |
| `V-API-RES-07` | P0 | WEB_CONTRACT | Multiple failures for same Supplier | Supplier appears once in `degradedSuppliers` | PLANNED |
| `V-API-RES-08` | P0 | WEB_CONTRACT | Search success | `Cache-Control: no-store` | PLANNED |

## Failure mapping

| ID | Priority | Method | Scenario | Expected | Status |
|---|---|---|---|---|---|
| `V-API-ERR-01` | P0 | WEB_CONTRACT | `SearchOutcome.Unavailable` | `503 SEARCH_UNAVAILABLE` | PLANNED |
| `V-API-ERR-02` | P0 | WEB_CONTRACT | Unexpected exception | `500 INTERNAL_ERROR` | PLANNED |
| `V-API-ERR-03` | P0 | WEB_CONTRACT | 503 result | `Cache-Control: no-store` | PLANNED |
| `V-API-ERR-04` | P0 | WEB_CONTRACT | Supplier auth/rate-limit internal failure | No public 401/429 passthrough | PLANNED |
| `V-API-ERR-05` | P0 | WEB_CONTRACT | Catalog bootstrap gate CLOSED | `503 SEARCH_UNAVAILABLE`; unavailable-baseline Supplier IDs exposed, no raw cause | PLANNED |

---

# 21. Mock Supplier Verification

The Mock Supplier is a separate executable component.
Keep verification shallow.

| ID | Priority | Method | Scenario | Expected | Status |
|---|---|---|---|---|---|
| `V-MCK-01` | P0 | COMPONENT_TEST | Supplier A catalog normal | Valid A catalog response | PLANNED |
| `V-MCK-02` | P0 | COMPONENT_TEST | Supplier B catalog normal | Valid B catalog response | PLANNED |
| `V-MCK-03` | P0 | COMPONENT_TEST | A availability normal | Normal response | PLANNED |
| `V-MCK-04` | P0 | COMPONENT_TEST | A availability supplier-error | Declared error behavior | PLANNED |
| `V-MCK-05` | P0 | COMPONENT_TEST | A availability no-response | Response intentionally withheld | PLANNED |
| `V-MCK-06` | P0 | COMPONENT_TEST | B search normal | `resultCode=0000` | PLANNED |
| `V-MCK-07` | P0 | COMPONENT_TEST | B search supplier-error | Non-`0000` resultCode | PLANNED |
| `V-MCK-08` | P0 | COMPONENT_TEST | B search no-response | Response intentionally withheld | PLANNED |

Catalog endpoints need no error mode unless later required.

---

# 22. End-to-End Acceptance Verification

E2E proves critical vertical slices only.
Do not replicate every unit/adapter edge case here.

Use:

```text
main application
PostgreSQL
real :mock-supplier application
```

| ID | Priority | Scenario | Expected | Status |
|---|---|---|---|---|
| `V-E2E-01` | P0 | Startup Catalog sync → mappings persisted → A/B Search normal | `200 COMPLETE`, internal IDs + live Offers | PLANNED |
| `V-E2E-02` | P0 | Supplier A HTTP error + B normal | `200 PARTIAL`, B Offers preserved | PLANNED |
| `V-E2E-03` | P0 | A normal + B HTTP 200 with `E503` | `200 PARTIAL` | PLANNED |
| `V-E2E-04` | P0 | A no-response + B normal | A timeout does not block B; `200 PARTIAL` | PLANNED |
| `V-E2E-05` | P0 | Every relevant Supplier fails after baseline established | `503 SEARCH_UNAVAILABLE` | PLANNED |
| `V-E2E-06` | P0 | One required stay date inventory = 0 | Offer returned with `availableRooms=0` | PLANNED |
| `V-E2E-07` | P0 | >50 mapped Properties for one Supplier | Multiple requests; valid combined result | PLANNED |
| `V-E2E-08` | P1 | Same Catalog synced again | Public internal IDs remain stable | PLANNED |
| `V-E2E-09` | P0 | Fresh DB; A Catalog bootstrap fails, B succeeds; Search endpoint called | `503 SEARCH_UNAVAILABLE`, not `200` empty | PLANNED |
| `V-E2E-10` | P0 | Existing A state; A startup refresh fails; B refresh succeeds | Search remains available using A stale Catalog baseline | PLANNED |
| `V-E2E-11` | P1 | Baseline established but no searchable Catalog targets | `200 COMPLETE`, empty offers, no Supplier availability call | PLANNED |

---

# 23. Architecture Verification

Gradle module dependencies are the primary enforcement mechanism.
Add architecture tests only where Gradle boundaries cannot express the rule.

| ID | Priority | Method | Rule | Status |
|---|---|---|---|---|
| `V-ARCH-01` | P0 | ARCH_STATIC | Domain/API/Port modules do not depend on Spring/JPA/WebClient | PASSING |
| `V-ARCH-02` | P0 | ARCH_STATIC | Search does not depend on Catalog internal modules | PASSING |
| `V-ARCH-03` | P0 | ARCH_STATIC | Catalog/Search do not depend on concrete Supplier implementations | PASSING |
| `V-ARCH-04` | P0 | ARCH_STATIC | Supplier adapters do not depend on Catalog/Search application/adapters | PASSING |
| `V-ARCH-05` | P0 | ARCH_STATIC | `:shared:infrastructure` has no Catalog/Search dependency | PASSING |
| `V-ARCH-06` | P0 | ARCH_STATIC | Web adapter does not depend on persistence adapter | PASSING |
| `V-ARCH-07` | P0 | ARCH_STATIC / review | `:app` contains composition/bootstrap, not business rules | MANUAL-PASS |
| `V-ARCH-SUP-01` | P0 | ARCH_STATIC / review | `SupplierId` representation inspected | String-backed value object in `:catalog:api`; no closed A/B enum | PLANNED |

---

## Supplier Wiring

| ID | Priority | Method | Scenario | Expected | Status |
|---|---|---|---|---|---|
| `V-WIRE-SUP-01` | P0 | CROSS_MODULE | Configured A/B with matching Catalog + Availability Port registrations | Bootstrap wiring validation passes | PLANNED |
| `V-WIRE-SUP-02` | P0 | CROSS_MODULE | Configured Supplier has no Catalog Port | Application bootstrap fails fast before Catalog sync/Search | PLANNED |
| `V-WIRE-SUP-03` | P0 | CROSS_MODULE | Configured Supplier has no Availability Port | Application bootstrap fails fast before Catalog sync/Search | PLANNED |
| `V-WIRE-SUP-04` | P1 | APPLICATION_UNIT / review | Construct `SupplierId("C")` without changing shared identity code | Valid identifier; support still absent until adapter/config registration | PLANNED |
| `V-WIRE-SUP-05` | P1 | APPLICATION_UNIT | Blank SupplierId | Rejected by value-object invariant | PLANNED |

---

## 23.1 Unified Information Policy

| ID | Priority | Method | Scenario | Expected | Status |
|---|---|---|---|---|---|
| `V-INFO-01` | P0 | SUPPLIER_INTEGRATION | Supplier A complete nightly price/tax rows | Correct whole-stay gross total; nightly/tax breakdown absent from common Search item | PLANNED |
| `V-INFO-02` | P0 | SUPPLIER_INTEGRATION | Supplier B valid whole-stay total with tax-inclusive semantics | `totalPrice` preserved; no fabricated nightly/tax values | PLANNED |
| `V-INFO-03` | P0 | SUPPLIER_INTEGRATION | Supplier B incompatible/malformed tax semantics | Rejected according to normalization policy | PLANNED |
| `V-INFO-04` | P0 | APPLICATION_UNIT | Complete daily inventory `[3,1,5]` | `availableRooms=1`; daily series need not appear in public DTO | PLANNED |
| `V-INFO-05` | P0 | APPLICATION_UNIT | Availability response repeats conflicting name/maxOccupancy | Search Offer uses Catalog metadata authority | PLANNED |
| `V-INFO-06` | P0 | WEB_CONTRACT | Successful Offer response inspected | No external Property/Room codes or nightly/tax breakdown leaked | PLANNED |
| `V-INFO-07` | P0 | WEB_CONTRACT | Partial Supplier failure | Public response contains degraded Supplier fact, not raw upstream code/body | PLANNED |

---

# 24. Technology / Build Verification

| ID | Priority | Method | Verification | Status |
|---|---|---|---|---|
| `V-TECH-01` | P0 | BUILD_CHECK | JDK >= 21; project target currently JDK 25 | PASSING |
| `V-TECH-02` | P0 | BUILD_CHECK | Spring Boot >= 3.4; project currently 4.1.1 | PLANNED |
| `V-TECH-03` | P0 | BUILD_CHECK | Gradle/Kotlin DSL build succeeds | PASSING |
| `V-TECH-04` | P0 | ARCH_STATIC / dependency review | Supplier HTTP adapters use WebClient | PLANNED |
| `V-TECH-05` | P0 | BUILD_CHECK | PostgreSQL-backed main application starts | PLANNED |
| `V-TECH-06` | P0 | BUILD_CHECK | Separate Mock Supplier executable starts | PLANNED |

Canonical build gates:

```text
./gradlew clean test
./gradlew build
```

Both must pass before release.

---

# 25. Observability Verification

Current Integration metrics:

```text
supplier request count
supplier request duration
supplier outcome/failure
timeout count
normalization failure count
```

Low-cardinality dimensions only:

```text
supplier
operation
outcome
```

| ID | Priority | Method | Scenario | Expected | Status |
|---|---|---|---|---|---|
| `V-OBS-01` | P1 | SUPPLIER_INTEGRATION | Normal request | request/timer metric emitted | PLANNED |
| `V-OBS-02` | P1 | SUPPLIER_INTEGRATION | Timeout | timeout/failure metric emitted | PLANNED |
| `V-OBS-03` | P1 | SUPPLIER_INTEGRATION | Normalization failure | normalization failure metric emitted | PLANNED |
| `V-OBS-04` | P0 | ARCH_STATIC / review | Metric tags inspected | No Property/Room/external IDs as labels | PLANNED |
| `V-OBS-05` | P1 | MANUAL_SMOKE | Main app running | Actuator/Micrometer endpoint exposes metrics | PLANNED |

---

# 26. OpenAPI Verification

| ID | Priority | Method | Verification | Status |
|---|---|---|---|---|
| `V-API-DOC-01` | P1 | MANUAL_SMOKE | Swagger/OpenAPI endpoint accessible | PLANNED |
| `V-API-DOC-02` | P1 | DOC_REVIEW | Search query parameters documented | PLANNED |
| `V-API-DOC-03` | P1 | DOC_REVIEW | `200/400/503/500` contracts documented | PLANNED |
| `V-API-DOC-04` | P1 | DOC_REVIEW | Snowflake IDs documented as strings | PLANNED |
| `V-API-DOC-05` | P1 | DOC_REVIEW | COMPLETE/PARTIAL/degraded Supplier semantics documented | PLANNED |

Generated OpenAPI must match `API.md`.

---

# 27. Documentation Verification

README is reader-facing. Detailed design may live under `docs/`.

| ID | Priority | Method | Verification | Status |
|---|---|---|---|---|
| `V-DOC-01` | P0 | DOC_REVIEW | README contains build/run instructions | PLANNED |
| `V-DOC-02` | P0 | DOC_REVIEW | README explains MVC + WebClient programming model | PLANNED |
| `V-DOC-03` | P0 | DOC_REVIEW | README explains unified Property/RoomType model | PLANNED |
| `V-DOC-04` | P0 | DOC_REVIEW | README explains stable Supplier-to-internal mapping | PLANNED |
| `V-DOC-05` | P0 | DOC_REVIEW | README explains Catalog sync timing/lifecycle | PLANNED |
| `V-DOC-06` | P0 | DOC_REVIEW | README explains partial failure/timeout strategy | PLANNED |
| `V-DOC-07` | P0 | DOC_REVIEW | README explains preserved/dropped information and rationale | PLANNED |
| `V-DOC-08` | P0 | DOC_REVIEW | README explains thousands-scale batching/concurrency approach | PLANNED |
| `V-DOC-09` | P1 | DOC_REVIEW | Design docs are linked/navigable | PLANNED |
| `V-DOC-10` | P1 | DOC_REVIEW | `JOURNAL.md` records meaningful engineering decisions/problems rather than routine task history | PLANNED |
| `V-DOC-11` | P0 | DOC_REVIEW | Domain scope/evolution review | Core, adjacent, infrastructure evolution, unmodeled concepts, and explicit OOS are separated; future BCs are not prematurely fixed | PLANNED |
| `V-DOC-12` | P1 | DOC_REVIEW | README links root-level `JOURNAL.md` and `AI_USAGE.md`; process docs do not override canonical design sources | PLANNED |

Public documentation must not copy or substantially reconstruct non-project external reference material.

---

# 28. AI Usage Verification

AI use must remain transparent and human-owned.

| ID | Priority | Method | Verification | Status |
|---|---|---|---|---|
| `V-AI-01` | P0 | DOC_REVIEW | Root-level `AI_USAGE.md` exists | PLANNED |
| `V-AI-02` | P0 | DOC_REVIEW | Entries summarize what was asked without raw prompt/external-reference dumps | PLANNED |
| `V-AI-03` | P0 | DOC_REVIEW | Accepted/modified/rejected/deferred suggestions are distinguishable when applicable | PLANNED |
| `V-AI-04` | P0 | REPO_REVIEW | No prompt injection/manipulation content | PLANNED |
| `V-AI-05` | P0 | MANUAL_SMOKE / review | Maintainer can explain committed implementation | PLANNED |
| `V-AI-06` | P0 | DOC_REVIEW | Significant entries include final human reasoning and verification/evidence, not AI suggestion alone | PLANNED |
| `V-AI-07` | P1 | DOC_REVIEW | AI usage record omits routine autocomplete/formatting/trivial boilerplate and does not invent retrospective failures | PLANNED |

Do not commit AI-generated code the maintainer cannot explain.

---

# 29. Public Repository Verification

Run before final release.

| ID | Priority | Method | Verification | Status |
|---|---|---|---|---|
| `V-REP-01` | P0 | REPO_REVIEW | Current tracked tree contains no non-redistributable external reference material | PLANNED |
| `V-REP-02` | P0 | REPO_REVIEW | Restricted organization/process-identifying strings are absent from current public artifacts and commit messages | PLANNED |
| `V-REP-03` | P0 | REPO_REVIEW | API keys/secrets/passwords are absent from current tree and Git history | PLANNED |
| `V-REP-04` | P0 | REPO_REVIEW | Git history has meaningful incremental commits | PLANNED |
| `V-REP-05` | P0 | REPO_REVIEW | All required project-authored artifacts live in repository | PLANNED |
| `V-REP-06` | P0 | REPO_REVIEW | README describes the system from implemented behavior/design in original project wording | PLANNED |
| `V-REP-07` | P0 | REPO_REVIEW | No reviewer/automated-review manipulation or prompt injection | PLANNED |
| `V-REP-08` | P0 | DOC_REVIEW / REPO_REVIEW | Public docs do not reconstruct external references via copied section structure, exhaustive field dictionaries, or large reference payload reproduction | PLANNED |
| `V-REP-09` | P0 | REPO_REVIEW | Complete Git history reviewed for restricted commit messages and historical filenames | PLANNED |
| `V-REP-10` | P0 | REPO_REVIEW | Complete Git history contains no non-redistributable external-reference blobs or leaked secrets | PLANNED |

Suggested checks cover two scopes.

### Current tree

```text
git status / tracked-file review
git ls-files
grep/ripgrep for restricted identifying phrases
secret scanner
```

### Complete Git history

```text
git log --all --format='%H %s'
git log --all --name-only --pretty=format:
git rev-list --objects --all
history-capable secret scan where available
```

If prohibited material or a secret was committed historically, deleting it in a later commit is insufficient.
Rewrite the affected history and re-run the repository checks before public release.

Exact commands/tooling may vary by environment.

---

# 30. Requirement Trace Matrix

Maps `REQUIREMENTS.md` families to primary evidence.

## Repository / Submission (`REP`)

```text
REP public-repository safety
→ V-REP-01..10

REP buildable/self-contained artifacts
→ V-TECH-03
→ V-DOC-01
```

## Technology (`TEC`)

```text
TEC Java/Kotlin + Spring Boot + Gradle + RDB
→ V-TECH-01..05

TEC Supplier WebClient
→ V-TECH-04
→ V-INT-A-REQ-*
→ V-INT-B-REQ-*
```

## Catalog / Mapping (`CAT`)

```text
CAT stable Supplier → internal Property mapping
→ V-CAT-DOM-06
→ V-CAT-UC-01..04
→ V-PER-CON-01

CAT stable RoomType mapping scoped by Property
→ V-CAT-DOM-01..05
→ V-PER-CON-02

CAT Catalog synchronization
→ V-CAT-UC-01..08
→ V-CAT-TX-01..03

DEC-CAT-001 startup baseline / Search readiness
→ V-CAT-BOOT-01..05
→ V-PER-BASE-01..02
→ V-API-ERR-05
→ V-E2E-09..11

CAT searchable mappings
→ V-CAT-READ-*
→ V-PER-READ-*
```

## Supplier Contract (`CON`)

```text
CON request propagation/authentication
→ V-INT-A-REQ-*
→ V-INT-B-REQ-*

CON Supplier A protocol/price
→ V-INT-A-CAT-*
→ V-INT-A-PRICE-*
→ V-INT-A-ERR-01

CON Supplier B protocol/resultCode/price
→ V-INT-B-PROTO-*
→ V-INT-B-PRICE-*

CON partial normalization
→ V-INT-NORM-*
```

### Supplier identity / extensibility

```text
CON-007
DEC-CON-001
→ V-ARCH-SUP-01
→ V-WIRE-SUP-01..05
```

---

## Search (`SEA`)

```text
SEA SearchCondition
→ V-SEA-PERIOD-*
→ V-SEA-GUEST-*

SEA whole-stay availability
→ V-SEA-AVAIL-*

SEA all searchable products
→ V-SEA-UC-01..10

SEA max-50 batching / scale
→ V-INT-BATCH-*
→ V-E2E-07

SEA Supplier parallelism
→ V-SEA-CON-01..02
```

### Unified information policy

```text
DOC-002
DEC-SEA-002
SEA-003
SEA-004
SEA-005
→ V-INFO-01..07
```

---

### Batch concurrency / execution latency

```text
SEA-011
→ V-INT-CON-01..05

DEC-RES-001
→ V-INT-LAT-01..02
→ V-RES-TIMEOUT-01..02
```

---

## Resilience (`RES`)

```text
RES timeout
→ V-RES-TIMEOUT-01..02
→ V-E2E-04

RES connection failure
→ V-RES-CONN-01

RES partial Supplier failure
→ V-SEA-UC-02..04
→ V-E2E-02..05

RES unified B failure judgment
→ V-INT-B-PROTO-02..06
→ V-E2E-03
```

## Mock (`MCK`)

```text
MCK normal/error/no-response
→ V-MCK-01..08
```

## Documentation (`DOC`)

```text
DOC README/design rationale
→ V-DOC-01..11

DOC OpenAPI
→ V-API-DOC-01..05
```

## Testing (`TST`)

```text
TST automated coverage by ownership
→ Sections 5–23

TST build gate
→ V-TECH-03
```

## Observability (`OBS`)

```text
OBS Supplier metrics
→ V-OBS-01..05
```

## AI (`AI`)

```text
AI usage transparency/human ownership
→ V-AI-01..07
```

---

# 31. Optional Feature Verification

Retry / Circuit Breaker are not core requirements.

If not implemented:

```text
no verification required
```

If implemented:

```text
assign P2 scenarios
verify bounded attempts
verify no retry on auth/validation failures
verify latency/request amplification remains bounded
```

Optional work must not delay P0 scenarios.

---

# 32. Evidence Mapping During Implementation

Scenario IDs are stable. Concrete test class/file names may evolve.

Example:

```text
V-SEA-AVAIL-01
→ StayAvailabilityTest

V-SEA-UC-02
→ SearchStaysServiceTest

V-INT-B-PROTO-06
→ SupplierBAvailabilityAdapterTest

V-E2E-04
→ SearchFlowE2ETest
```

Do not make implementation class names part of semantic verification IDs.

Agent work updates status/evidence only after the corresponding test/check actually passes.

### Baseline module evidence

```text
V-ARCH-01 .. V-ARCH-06
→ verifyModuleBoundaries (root Gradle task)

V-ARCH-07
→ static review of app/src/main/kotlin/com/staysupplierhub/Application.kt

V-TECH-01, V-TECH-03
→ ./gradlew.bat check --console=plain --no-daemon
   (JDK 25 toolchain, multi-module Gradle/Kotlin DSL check)
```

`V-TECH-02` remains planned until the PostgreSQL-backed application can start.
`V-TECH-04` remains planned until Supplier adapters make and verify WebClient calls.

### Catalog identity domain evidence

```text
CAT-001, CAT-002, CAT-005
→ V-CAT-DOM-01 .. V-CAT-DOM-07
→ catalog/domain/src/test/kotlin/com/staysupplierhub/catalog/domain/PropertyTest.kt

SupplierId representation support
→ catalog/api/src/test/kotlin/com/staysupplierhub/catalog/api/CatalogIdsTest.kt
```

### Catalog synchronization application evidence

```text
CAT-003, CAT-004, CAT-005, CAT-006, CAT-007
→ V-CAT-UC-01 .. V-CAT-UC-08
→ catalog/application/src/test/kotlin/com/staysupplierhub/catalog/application/SynchronizeSupplierCatalogServiceTest.kt
```

### Catalog published read evidence

```text
CAT-003, CAT-004, CAT-006, CAT-007, SEA-009
→ V-CAT-READ-01 .. V-CAT-READ-05
→ catalog/application/src/test/kotlin/com/staysupplierhub/catalog/application/ReadSearchableCatalogServiceTest.kt
```

### Catalog persistence evidence

```text
V-PER-MAP-01 .. V-PER-MAP-02
V-PER-BASE-01 .. V-PER-BASE-02
V-PER-READ-01 .. V-PER-READ-03
→ catalog/adapter/persistence/src/test/kotlin/com/staysupplierhub/catalog/adapter/persistence/CatalogPersistenceAdapterTest.kt
→ PostgreSQL Testcontainers
```

### Snowflake infrastructure evidence

```text
V-ID-01 .. V-ID-05
→ shared/infrastructure/src/test/kotlin/com/staysupplierhub/shared/infrastructure/id/SnowflakeIdGeneratorTest.kt
```

---

# 32.1 Agent Workflow Verification

These checks validate the repository's agent harness rather than product runtime behavior.

| ID | Priority | Method | Verification | Status |
|---|---|---|---|---|
| `V-AGT-01` | P0 | DOC_REVIEW | `AGENTS.md` canonical-document paths resolve to actual repository files | PLANNED |
| `V-AGT-02` | P1 | DOC_REVIEW | Agent checkpoint rule stops after one coherent slice and reports Requirement/V/evidence | PLANNED |
| `V-AGT-03` | P1 | DOC_REVIEW | Journal/AI record-candidate criteria prevent routine/no-value logging | PLANNED |
| `V-AGT-04` | P1 | DOC_REVIEW | Commit-boundary guidance prefers coherent behavior + verification, not file-based commits | PLANNED |

---

# 33. Completion Gate

Release-ready only when:

```text
[ ] every REQUIRED requirement has evidence

[ ] every PROJECT-MUST requirement has evidence

[ ] every P0 automated scenario is PASSING

[ ] every P0 manual/static scenario is MANUAL-PASS

[ ] ./gradlew clean test passes

[ ] ./gradlew build passes

[ ] core E2E flow passes

[ ] A/B parallel execution is proven

[ ] timeout + partial-failure flow is proven

[ ] Supplier B HTTP-200 business failure is proven

[ ] >50 Property batching is proven

[ ] stable Catalog IDs are proven

[ ] multi-night minimum inventory is proven

[ ] README build/run/design rationale is complete

[ ] broader domain scope is documented without speculative BC/module commitments

[ ] OpenAPI matches API.md

[ ] required observability is present

[ ] AI usage documentation is present

[ ] no secrets are tracked

[ ] non-project external reference material is absent from the current tracked tree and Git history

[ ] restricted identifying-string scan passes for current artifacts and Git history

[ ] public docs do not reconstruct external reference material

[ ] AGENTS canonical document paths resolve correctly

[ ] no prompt injection/manipulation exists

[ ] maintainer can explain every committed core design/implementation choice
```

---

# 34. Guardrails

```text
test count ≠ confidence

E2E ≠ place for every edge case

unit test ≠ proof of module wiring

H2 ≠ PostgreSQL verification

elapsed-time assertion ≠ preferred concurrency proof

Supplier mock behavior ≠ Supplier adapter correctness

optional feature ≠ prerequisite for mandatory completion

PASSING status ≠ declared before evidence exists
```

Verification should make failures local and diagnosable.

A failed Domain invariant test should point to Domain logic.
A failed Supplier adapter test should point to integration semantics.
A failed E2E should indicate broken collaboration, not become the only place business rules are tested.
