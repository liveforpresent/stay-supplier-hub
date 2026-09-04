# INTEGRATION.md

> Canonical Supplier integration semantics.
>
> - Requirements: `REQUIREMENTS.md`
> - Domain: `DOMAIN.md`
> - Application flow: `USE_CASES.md`
> - Terminology: `GLOSSARY.md`
> - Modules/runtime: `ARCHITECTURE.md`
>
> Defines **Supplier protocol translation, Port semantics, failure handling, batching, timeout,
> configuration, and Supplier extensibility**.
> Search domain rules, API DTOs, persistence, and Gradle structure belong elsewhere.

---

## 0.1 Documentation Boundary

This document explains the Supplier protocol facts and semantic differences that materially affect the adapter
implementation.

It is **not** an external API reference manual.

Include:

```text
endpoint/method required by implemented adapter behavior
authentication requirement
request-size constraint
success/failure interpretation
price/inventory semantics
normalization and resilience rules
```

Do not add merely for completeness:

```text
copied external-document section structure
exhaustive external field dictionaries
large reference payload reproductions
unneeded sample identifiers/prices
```

Project-owned DTOs, Mock Supplier fixtures, tests, and focused examples remain the implementation evidence for
concrete wire shapes.

---

## 1. Principles

Supplier Integration is an **Anti-Corruption Layer (ACL) / outbound adapter boundary**, not a Bounded Context.

```text
Supplier protocol
      ↓
ACL / Adapter
      ↓
Catalog/Search-owned contract
```

Rules:

- Catalog/Search own the Port semantics they require.
- Integration absorbs Supplier-specific protocol differences.
- Supplier DTOs and raw protocol errors never leak into business contexts.
- Supplier-dependent semantics belong in Integration.
- Supplier-independent lodging rules belong in Search.
- Live availability responses never mutate Catalog metadata.

---

## 1.1 Information Retention & Normalization Policy

Supplier payloads are not copied wholesale into a shared model. Each field is handled by one of four policies:

```text
PRESERVE
→ required downstream meaning remains available

DERIVE / NORMALIZE
→ Supplier-specific representation becomes a common semantic value

VALIDATE THEN DROP
→ used to establish correctness but omitted from the common model

DISCARD
→ no stable downstream business meaning, or another context is authoritative
```

### Catalog information

| Supplier information | Policy | Internal meaning |
|---|---|---|
| Supplier identity | PRESERVE | `SupplierId` |
| Property code | PRESERVE | `SupplierPropertyCode` for stable mapping |
| Property name | PRESERVE | Catalog `PropertyName` |
| RoomType code | PRESERVE | `SupplierRoomTypeCode` for stable mapping |
| RoomType name | PRESERVE | Catalog `RoomTypeName` |
| maxOccupancy | PRESERVE | Catalog RoomType characteristic |
| Supplier envelope/protocol fields | DISCARD | Adapter-only protocol |
| raw result/error code | NORMALIZE THEN DROP | Consumer-owned typed failure |

### Availability / commercial information

| Supplier information | Policy | Internal meaning |
|---|---|---|
| Currency | PRESERVE | `WholeStayPrice.currency` |
| Breakfast inclusion | PRESERVE | `BreakfastIncluded` |
| Daily inventory dates/counts | PRESERVE TRANSIENTLY → DERIVE | `DailyInventory[]` → Search `StayAvailability` |
| Supplier A nightlyRate/taxAmount | DERIVE THEN DROP | Whole-stay gross total |
| Supplier B totalPrice | PRESERVE / NORMALIZE | Whole-stay gross total |
| Supplier B taxIncluded | VALIDATE THEN DROP | Confirms compatible gross-total semantics |
| Supplier B nightly/tax breakdown | NEVER INVENT | Supplier does not provide it |
| Availability Property/Room names | DISCARD AS AUTHORITY | Catalog metadata is authoritative |
| Availability maxOccupancy | DISCARD AS AUTHORITY | Catalog metadata is authoritative |
| External Property/Room codes | PRESERVE TRANSIENTLY | Join to Catalog internal IDs |
| raw Supplier errors | NORMALIZE THEN HIDE | Internal failure semantics / public degraded fact |

The common model is deliberately based on **the smallest truthful semantics needed by integrated Search** rather
than on the richest Supplier payload.

Avoid a shared superset such as:

```text
UnifiedPrice
├── nightlyRate?
├── nightlyTax?
├── totalPrice
└── ...
```

when some Suppliers cannot truthfully populate those fields.

---

# 2. Port Semantics

## 2.1 Catalog Port

Conceptually:

```text
SupplierCatalogPort

fetchCatalog()
→ SupplierCatalogOutcome
```

Outcome:

```text
SupplierCatalogOutcome
├── Success(SupplierCatalogSnapshot)
└── Failed(SupplierFailure)
```

Neutral snapshot:

```text
SupplierCatalogSnapshot
└── Properties[]
    ├── SupplierPropertyCode
    ├── PropertyName
    └── RoomTypes[]
        ├── SupplierRoomTypeCode
        ├── RoomTypeName
        └── MaxOccupancy
```

It must not contain internal `PropertyId`, `RoomTypeId`, persistence models, or Supplier DTOs.

`Success` means the complete Supplier catalog was fetched and normalized safely enough for
snapshot reconciliation. Otherwise the Port returns `Failed`.

---

## 2.2 Availability Port

Conceptually:

```text
SupplierAvailabilityPort

search(
    SupplierPropertyTargets,
    SearchCondition
)
→ SupplierAvailabilityOutcome
```

Search passes the complete external Property target set for one Supplier.
Integration owns Supplier-specific request splitting.

Target:

```text
SupplierPropertyTarget
└── SupplierPropertyCode
```

Outcome:

```text
SupplierAvailabilityOutcome
├── Completed
│   ├── Items[]
│   └── Failures[]
└── Failed
    └── Failures[]
```

`Completed` means at least one relevant request/batch produced a legitimate successful result,
including a legitimate empty result.

Examples:

```text
Completed(items=[...], failures=[])
Completed(items=[...], failures=[...])
Completed(items=[], failures=[])
Completed(items=[], failures=[...])
```

`Failed` means every relevant request/batch failed after transport, protocol, and normalization checks.

Neutral item:

```text
SupplierAvailabilityItem
├── SupplierPropertyCode
├── SupplierRoomTypeCode
├── WholeStayPrice
│   ├── Amount
│   └── Currency
├── DailyInventory[]
│   ├── Date
│   └── RemainingRooms
└── BreakfastIncluded
```

`Amount` uses the Supplier currency's minor unit as an integer.
`Currency` preserves the Supplier-provided ISO 4217 code.

Integration never owns or invents internal Catalog IDs.

---

## 2.3 Multi-Supplier Routing

Each Supplier adapter implements the consumer-owned Port for exactly one Supplier.

Conceptually:

```text
Catalog / Search Application
      ↓
Map<SupplierId, Port>
      ↓
Supplier A Adapter / Supplier B Adapter
```

The Application layer selects a Port implementation only by `SupplierId`.
It does not depend on Supplier-specific adapter classes.

The `Map<SupplierId, Port>` is assembled by the application Composition Root.

Therefore:

- no separate integration routing module is required,
- Catalog/Search do not depend on Supplier implementation modules,
- Supplier adapters depend inward on the Catalog/Search Port contracts,
- adding Supplier C requires registering its Port implementation in composition.

Concrete module wiring belongs in `ARCHITECTURE.md`.

---

# 3. Common Request Translation

Availability/search requests preserve the following semantics:

```text
checkIn
checkOut
adults
children
```

Rules:

- `adults` and `children` remain separate.
- Dates are serialized for Suppliers as `YYYY-MM-DD`.
- The stay interval is `[checkIn, checkOut)`.
- Supplier-specific parameter names are adapter-private.
- Search passes external Property codes; Integration does not require internal IDs.

---

# 4. Supplier A

## 4.1 Catalog

```text
GET /a/v1/hotels
X-Api-Key: <key>
```

Returns the handled Property/RoomType catalog.

Live price, inventory, and breakfast data are not Catalog state.

---

## 4.2 Availability

```text
GET /a/v1/availability
```

Supplier fields:

```text
hotelCodes
checkIn
checkOut
adults
children
```

`hotelCodes` is comma-separated and limited to 50 Property codes per request.

---

## 4.3 Price Semantics

Supplier A provides per-date values:

```text
date
remainingRooms
nightlyRate
taxAmount
```

`nightlyRate` is net of tax.

For each required stay date:

```text
customer daily price = nightlyRate + taxAmount
```

Normalized whole-stay price:

```text
WholeStayPrice =
Σ (nightlyRate + taxAmount)
for every required stay date
```

Before producing `WholeStayPrice`, the A adapter must verify that the price-bearing daily rows
cover the requested StayPeriod exactly once per required date.

Do not produce a partial total from missing, duplicate, or out-of-period rate rows.

After deriving a correct `WholeStayPrice`, Supplier A's nightly price/tax breakdown is intentionally not carried
into the common Search contract. The information is used for derivation; it is not ignored.

---

## 4.4 Failure Semantics

| Supplier A | Internal Failure |
|---|---|
| HTTP 400 | `INVALID_REQUEST` |
| HTTP 401 | `AUTHENTICATION_FAILED` |
| HTTP 429 | `RATE_LIMITED` |
| HTTP 500 | `SUPPLIER_ERROR` |
| HTTP 503 | `SERVICE_UNAVAILABLE` |

Declared non-success responses never produce normal Catalog/Search data.

---

# 5. Supplier B

## 5.1 Protocol Success

Supplier B uses body-level `resultCode` semantics, including Catalog responses.

```text
HTTP 200 ≠ Supplier success
```

Only:

```text
resultCode == "0000"
```

is successful.

A non-success `resultCode` must be translated before data reaches Catalog/Search.

---

## 5.2 Catalog

```text
GET /b/api/properties
X-Api-Key: <key>
```

A successful response requires `resultCode == "0000"` and a safely normalizable catalog payload.

---

## 5.3 Availability

```text
GET /b/api/search
```

Supplier fields:

```text
propertyIds
checkIn
checkOut
adults
children
```

`propertyIds` is comma-separated and limited to 50 Property codes per request.

---

## 5.4 Price Semantics

Supplier B provides:

```text
totalPrice
taxIncluded
```

`totalPrice` is the whole-stay gross total.

The adapter preserves `totalPrice` directly as `WholeStayPrice`.

The contract expects tax-inclusive semantics; an incompatible/malformed price representation
must not be silently reinterpreted.

Do not fabricate:

```text
nightly price
nightly tax
tax breakdown
```

---

## 5.5 Failure Semantics

| Supplier B | Internal Failure |
|---|---|
| `E400` | `INVALID_REQUEST` |
| `E401` | `AUTHENTICATION_FAILED` |
| `E429` | `RATE_LIMITED` |
| `E500` | `SUPPLIER_ERROR` |
| `E503` | `SERVICE_UNAVAILABLE` |

Example:

```text
HTTP 200 + resultCode=E503
→ SERVICE_UNAVAILABLE
```

---

# 6. Failure Model

Canonical failure types:

```text
INVALID_REQUEST
AUTHENTICATION_FAILED
RATE_LIMITED
SUPPLIER_ERROR
SERVICE_UNAVAILABLE
CONNECTION_FAILED
TIMEOUT
INVALID_RESPONSE
```

Transport mapping:

```text
DNS/refused/unreachable connection → CONNECTION_FAILED
connection or response timeout     → TIMEOUT
unsafe/unparseable protocol data   → INVALID_RESPONSE
```

Failure types are owned by the consuming Port contract.

Conceptually:

```text
Catalog Port → CatalogSupplierFailureType
Search Port  → SearchSupplierFailureType
```

Both currently use the same semantic taxonomy, but they are not forced into one shared Kotlin type.

The selected `Map<SupplierId, Port>` key already identifies the Supplier. Search may attach that
`SupplierId` when constructing its application-level failure result.

Raw upstream codes, messages, stack traces, and request details belong in diagnostics.

Expected Supplier failures are returned as typed outcomes.
Catalog/Search must not inspect WebClient or Supplier-specific exceptions.

---

# 7. Validation & Partial Normalization

Validation strength differs between Catalog synchronization and live Search.

## 7.1 Catalog: Fail Closed

Catalog synchronization treats a Supplier catalog as a complete snapshot.

Therefore any condition that prevents the complete snapshot from being trusted causes:

```text
SupplierCatalogOutcome.Failed
```

Examples:

```text
failed envelope/resultCode
unparseable response
missing mandatory identity
duplicate/ambiguous external identity
structurally invalid catalog item
```

Do not partially reconcile a catalog snapshot and then infer absence from it.

---

## 7.2 Availability: Preserve Valid Siblings

A successful availability/search response may contain independently invalid items.

### Batch/response-level failure

Examples:

```text
failed HTTP/resultCode
unparseable envelope
missing mandatory response structure
```

Result:

```text
affected batch fails
```

### Item-level invalidity

Examples:

```text
missing external Property/RoomType code
invalid price representation
invalid Supplier-specific price coverage
otherwise non-normalizable item
```

Result:

```text
drop invalid item
preserve valid sibling items
record INVALID_RESPONSE / normalization failure
```

If a non-empty successful Supplier payload contains no safely normalizable item, the affected
batch is treated as `INVALID_RESPONSE`.

A legitimate successful response containing zero items remains a successful empty batch.

Search later applies Supplier-independent rules such as mapping, occupancy, and availability validity.

---

# 8. Price / Availability Boundary

Canonical ownership:

```text
Supplier-specific price interpretation    → Integration
Supplier-specific price completeness      → Integration
daily inventory field normalization       → Integration

Catalog mapping                           → Search
occupancy compatibility                   → Search
inventory required-date completeness      → Search
AvailableRooms = min(...)                 → Search
```

The A adapter validates daily rows needed to construct a correct whole-stay price.
Search remains the authority for the Supplier-independent `StayAvailability` invariant.

Never invent missing Supplier data:

```text
Supplier B nightly price/tax
missing Supplier A price dates
internal PropertyId / RoomTypeId
missing inventory dates
```

---

# 9. Catalog Metadata Authority

Availability responses may repeat fields such as names or occupancy.

They do not update Catalog state.

Catalog metadata is updated only through Catalog synchronization.

Search uses Catalog's Published Read Contract for:

```text
PropertyId / PropertyName
RoomTypeId / RoomTypeName
MaxOccupancy
```

and Supplier availability results for live commercial data.

This prevents live response drift from silently changing stable Catalog metadata.

---

# 10. Batching & Concurrency

Search passes the complete target set for one Supplier.

Integration performs:

```text
targets
→ split by Supplier request limit
→ bounded concurrent requests
→ preserve successful batches
→ normalize failed batches
```

Current A/B availability request limit:

```text
max Property codes/request = 50
```

Search must not encode `50`.

Batch concurrency must be:

- explicitly bounded,
- configurable,
- independently configurable per Supplier,
- never unbounded.

Initial default:

```text
max batch concurrency = 5
```

This value is a **conservative initial tuning default**.

It is not:

```text
a business invariant
a Supplier protocol constant
a benchmark-derived optimum
a production SLA
```

Rationale:

- concurrency `1` would serialize independent external I/O and unnecessarily increase latency,
- unbounded or very high concurrency would allow one customer Search to create excessive upstream fan-out,
- no Supplier SLA, rate-limit capacity, or safe-concurrency contract is available to justify an aggressive value,
- with the current 50-Property request limit, concurrency `5` means at most five active batch requests per Supplier,
  representing at most 250 Property targets in active outbound calls at one time.

The value is independently configurable per Supplier and should be tuned from observed request latency, timeout,
rate-limit, and Supplier-failure metrics.

### Concurrency scope

The concurrency bound is **Supplier-global within one application instance**, not request-local.

```text
Supplier A adapter instance
└── shared limiter(max=5)
    ├── Search request #1 batches
    ├── Search request #2 batches
    └── Search request #3 batches
```

Do not construct a fresh limiter inside every Search invocation. Otherwise several concurrent Searches can each
consume their own full limit and defeat the upstream-protection purpose.

```text
wrong:
Search #1 → limiter(5)
Search #2 → limiter(5)
Search #3 → limiter(5)
→ Supplier may receive 15 concurrent requests

current policy:
Supplier A adapter → one shared limiter(5)
→ all Search requests compete for the same five permits
```

The current scope does not guarantee fairness between queued Searches or a distributed/global limit across
multiple application instances. Those are production bulkhead/admission-control concerns.

### End-to-end latency limitation

Per-request timeout and complete Supplier execution latency are different concepts.

Example:

```text
1000 Properties
→ 20 batches
batch concurrency = 5
response timeout = 2s
```

If every active request stalls until timeout, four waves may be required.

Therefore:

```text
response timeout = 2s
≠ Supplier execution deadline = 2s
```

Bounded concurrency protects upstream fan-out, but total Supplier execution time may still grow with queued batch
waves.

The current implementation keeps per-request timeouts and bounded concurrency as the core mechanism. A production
evolution may add a Supplier-level execution deadline/budget so unfinished or not-yet-started batch work can be
cancelled without discarding already completed batches.

Do not increase concurrency merely to manufacture a lower total timeout bound.

### Partial batch example

```text
Batch 1 success
Batch 2 failure
Batch 3 success
```

returns:

```text
Completed(
    items = valid successful items,
    failures = normalized failure(s)
)
```

If every batch fails after normalization:

```text
Failed(failures)
```

Batch indexes are integration implementation details.

---

# 11. Timeout, Authentication & Configuration

Initial defaults:

```text
connection timeout = 1s
response timeout   = 2s
```

Both are independently configurable.

Rationale:

- connection timeout fails fast when a Supplier cannot be reached,
- response timeout prevents a connected but stalled Supplier from dominating integrated search latency,
- these are project defaults for local/mock verification, not production SLAs.

Timeout due to connection establishment or response wait maps to:

```text
TIMEOUT
```

Other connection failures map to:

```text
CONNECTION_FAILED
```

One timed-out Supplier/batch must not discard successful independent results.

The Mock no-response scenario verifies response-timeout behavior.

Every Supplier request includes:

```text
X-Api-Key
```

Externalized per-Supplier configuration:

```text
base URL
API key
connection timeout
response timeout
batch concurrency
```

Credentials must never be hard-coded.

---

# 12. Observability

Minimum integration metrics:

```text
supplier request count
supplier request duration
supplier outcome/failure
timeout count
normalization failure count
```

These metrics also provide evidence for concurrency tuning. In particular, observe:

```text
request latency
TIMEOUT frequency
RATE_LIMITED / 429-equivalent frequency
Supplier error rate
```

A batch-count metric may be added if useful, but Property/Room identifiers and batch identifiers must not be used
as metric labels.

Low-cardinality dimensions:

```text
supplier
operation
outcome
```

Do not use identifiers such as Property/Room codes or IDs as metric labels.

High-cardinality diagnostics belong in structured logs.

---

# 13. Adding Supplier C

A new Supplier is added by adapting its protocol to the existing Catalog/Search-owned contracts.

Conceptually:

```text
Supplier C integration
├── client
├── catalog connector
├── availability connector
├── DTOs
├── protocol/failure translation
├── price translation
└── configuration
```

Expected unchanged:

```text
Catalog domain
Property / RoomType identity model
Search domain
StayOffer
StayAvailability rule
Supplier A/B implementations
```

Composition/routing registers Supplier C using a new `SupplierId` value.

`SupplierId` is deliberately not a closed A/B enum, so adding C does not require changing a central Supplier
identity type. Runtime support still requires explicit configuration and Port registration.

If Supplier C appears to require a shared-contract change, first determine whether:

1. the common business model is genuinely incomplete, or
2. the difference is Supplier-specific and belongs inside Supplier C's ACL.

Do not widen shared contracts merely to mirror a Supplier DTO.

---

# 14. Optional Resilience

Retry and circuit breaker are not part of the core implementation.

If retry is added:

- keep attempts bounded,
- use only for plausibly transient failures,
- account for the total latency/request budget,
- do not retry validation/authentication failures,
- do not amplify batched request pressure.

Circuit breaker is lower priority than timeout, partial failure, batching, bounded concurrency,
and failure normalization.

---

# 15. Guardrails

```text
Supplier Integration       ≠ Bounded Context
Supplier DTO               ≠ Domain model
Supplier raw error         ≠ stable failure contract
HTTP 200                   ≠ Supplier B success
request-size limit         ≠ Search domain rule
batch concurrency          ≠ Domain rule
Supplier-specific price    ≠ Search price logic
daily inventory            ≠ whole-stay availability
availability metadata      ≠ Catalog metadata authority
Supplier external code     ≠ internal Catalog ID
```

Avoid generic Supplier DTO abstractions that erase real semantic differences.

Share only semantically common contracts.

---

# 16. Requirement Trace

```text
Catalog integration       → CAT / CON
Availability integration  → SEA / CON
Request propagation       → CON
Failure normalization     → RES / CON
Batching/concurrency      → SEA / PROJECT-MUST
Timeout/authentication    → RES / CON
Supplier extension        → DOC
Observability             → OBS
```

Detailed requirement-to-test mapping belongs in `VERIFICATION.md`.
