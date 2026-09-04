# USE_CASES.md

> Canonical application-flow specification.
>
> - Requirements: `REQUIREMENTS.md`
> - Domain model/invariants: `DOMAIN.md`
> - Terminology: `GLOSSARY.md`
> - Modules/transactions/concurrency: `ARCHITECTURE.md`
> - Supplier protocol details: `INTEGRATION.md`
>
> Defines **application capabilities, orchestration, and boundary ownership**.
> Framework, package, DTO, SQL, HTTP, and concrete configuration details belong elsewhere.

---

## 1. Scope

```text
Catalog
├── UC-CAT-01 SynchronizeSupplierCatalog
└── Q-CAT-01 ReadSearchableCatalog

Search
└── UC-SEA-01 SearchStays
```

Do not create standalone Use Cases for startup/bootstrap, Supplier client calls, batch splitting,
price normalization, availability calculation, or response formatting.

---

# 2. UC-CAT-01 SynchronizeSupplierCatalog

## Purpose

Synchronize **one Supplier** catalog with the internal Catalog while preserving stable Property/RoomType identity.

## Input

```text
SupplierId
```

## Main Flow

```text
1. Fetch the Supplier catalog through the Catalog-owned outbound contract.

2. Supplier Integration:
   - calls the Supplier,
   - interprets Supplier-specific protocol,
   - returns a neutral Catalog snapshot.

3. Validate the snapshot as complete and safe to reconcile.

4. Load current Catalog state for that Supplier.

5. Reconcile Properties:
   existing → preserve PropertyId, update metadata, activate
   new      → create PropertyId as ACTIVE
   missing  → mark INACTIVE

6. Reconcile RoomTypes inside each present Property:
   existing → preserve RoomTypeId, update metadata, activate
   new      → create RoomTypeId as ACTIVE
   missing  → mark INACTIVE

7. Persist the Supplier reconciliation atomically.

8. Return synchronization outcome to the caller.
```

## Failure Rules

```text
Supplier request failure
OR snapshot validation failure
→ preserve current Catalog state
→ do not infer absence

Persistence failure
→ rollback affected Supplier synchronization
```

## Rules Used

- Property external identity = `SupplierId + SupplierPropertyCode`
- RoomType external identity is scoped by Property
- repeated external identity preserves internal ID
- inactive identity reappearing is reactivated with the same ID
- physical deletion is not normal reconciliation behavior
- `Property` is Aggregate Root; `RoomType` is its Entity

## Transaction Semantics

```text
external fetch
→ validate / normalize
→ begin persistence transaction
→ reconcile
→ commit
```

Supplier network communication occurs outside the DB transaction.

---

# 3. Q-CAT-01 ReadSearchableCatalog

## Purpose

Expose only the Catalog information Search requires, without exposing Catalog repositories,
persistence entities, or Aggregate internals.

This is a **Published Read Capability**, not an end-user Use Case.

## Consumer

```text
Search Bounded Context
```

## Output Semantics

Return only searchable state:

```text
Property.ACTIVE
AND
RoomType.ACTIVE
```

A Property with no ACTIVE RoomType need not be returned.

The contract exposes enough information to:

- group targets by Supplier,
- call Supplier APIs using external Property codes,
- map Supplier RoomType codes back to internal IDs,
- construct `StayOffer`.

Conceptually:

```text
Property
├── PropertyId
├── PropertyName
├── SupplierId
├── SupplierPropertyCode
└── RoomTypes[]
    ├── RoomTypeId
    ├── SupplierRoomTypeCode
    ├── RoomTypeName
    └── MaxOccupancy
```

Concrete type/module names belong in `ARCHITECTURE.md`.

## Boundary Rules

Search must not directly depend on:

```text
Catalog Repository
Catalog persistence entity
Catalog Aggregate internals
```

The Published Read Contract is not a Catalog Aggregate, Search Aggregate, View Model, or HTTP DTO.

---

# 4. UC-SEA-01 SearchStays

## Purpose

Produce unified live accommodation offers for one `SearchCondition` across all currently searchable Supplier products.

## Input

```text
SearchCondition
├── StayPeriod
│   ├── CheckIn
│   └── CheckOut
└── GuestComposition
    ├── Adults
    └── Children
```

## Output

Conceptually:

```text
SearchOutcome

├── Result
│   ├── StayOffer[]
│   └── SupplierFailure[]
│
└── Unavailable
    └── SupplierFailure[]
```

Interpretation:

```text
Result(offers, [])
→ complete success

Result(offers, failures)
→ partial success

Result([], [])
→ successful search with no offers

Unavailable(failures)
→ every relevant Supplier execution failed
```

`Result` means the integrated search completed successfully enough to return a valid result.
It does not imply non-zero inventory.

HTTP status mapping belongs to the web adapter.

## Execution Precondition

`UC-SEA-01` assumes the application Search traffic gate is open.

```text
all configured Suppliers have usable Catalog baseline
→ gate OPEN
→ SearchStays may execute

any configured Supplier lacks usable Catalog baseline
→ gate CLOSED
→ SearchStays is not interpreted as an empty Search
```

The gate is an application/operational concern outside the Search domain and does not add a new
`SearchOutcome` variant.

Therefore:

```text
established Catalog + zero searchable targets
→ Result([], [])

unknown Catalog caused by failed bootstrap
→ normal SearchStays flow does not begin
```

## Main Flow

```text
1. Validate SearchCondition.

2. Read searchable Catalog data through Q-CAT-01.

3. If no searchable target exists after the Catalog-baseline precondition has been satisfied:
   → return Result([], [])
   → do not call Suppliers.

4. Group targets by Supplier.

5. Start relevant Supplier searches concurrently.

6. For each Supplier, pass:
   - that Supplier's external Property targets
   - SearchCondition
   to the Search-owned Supplier availability contract.

   Internal PropertyId/RoomTypeId remain Search-side.

7. Supplier Integration:
   - translates Supplier request fields,
   - batches by Supplier request-size constraints,
   - executes batches with bounded concurrency,
   - interprets Supplier-specific failures,
   - translates Supplier-specific price semantics,
   - preserves successful batch results when other batches fail.

8. Receive normalized Supplier outcomes.

9. Join normalized items to Catalog data using:
   - SupplierPropertyCode
   - SupplierRoomTypeCode

10. For each mapped item:
    - validate occupancy against Catalog MaxOccupancy,
    - validate complete daily inventory,
    - derive StayAvailability,
    - preserve whole-stay price/currency and OfferConditions,
    - construct StayOffer.

11. Invalid or unmapped items:
    - do not become StayOffer,
    - do not discard valid siblings,
    - are recorded as normalization failures.

12. Merge valid StayOffers and failures.

13. Return:
    - Result if at least one relevant Supplier execution succeeded,
    - Unavailable if every relevant Supplier execution failed.
```

## Success / Failure Semantics

Supplier execution success is independent of offer count.

```text
valid Supplier response + zero offers
→ success

Supplier data returned but no item can be normalized safely
→ invalid-response failure

some valid items + some invalid items
→ preserve valid items and record failure

some Supplier/batch failures + at least one successful Supplier execution
→ partial Result

all relevant Supplier executions fail
→ Unavailable
```

A partial `Result` may contain zero offers when a successful Supplier execution legitimately returns no offers while another Supplier fails.

---

# 5. Boundary Ownership

| Concern | Owner |
|---|---|
| Searchable internal/external mappings | Catalog Published Read Capability |
| Supplier grouping | Search |
| Supplier-level parallel orchestration | Search |
| Supplier request field translation | Supplier Integration |
| Authentication | Supplier Integration |
| Request-size limit and batch splitting | Supplier Integration |
| Bounded batch concurrency | Supplier Integration |
| Raw protocol/result-code interpretation | Supplier Integration |
| Supplier-specific price translation | Supplier Integration |
| Batch-level partial-result preservation | Supplier Integration |
| Match external codes to internal IDs | Search |
| Occupancy validation | Search |
| Whole-stay availability | Search |
| StayOffer construction | Search |
| Result vs Unavailable | Search |
| HTTP status/response mapping | Web adapter |

### Price

Supplier Integration provides normalized:

```text
whole-stay amount + currency
```

Search does not inspect Supplier-specific tax fields or fabricate missing nightly/tax breakdowns.

### Availability

Supplier Integration provides normalized daily inventory.

Search derives:

```text
AvailableRooms =
    min(RemainingRooms for every required StayPeriod date)
```

An item is invalid for missing/duplicate/out-of-period dates or negative inventory.

`AvailableRooms = 0` is a valid StayOffer, not failure.

### Identity Join

Supplier Integration never owns or invents internal IDs.

```text
Catalog external mapping
+
normalized Supplier external identity
→ Search joins
→ internal PropertyId / RoomTypeId
→ StayOffer
```

---

# 6. Concurrency & Transaction Guardrails

## Catalog Synchronization

```text
Supplier network call
→ outside DB transaction

validated reconciliation
→ inside persistence transaction
```

## Search

Do not hold a long-running DB transaction while waiting for Supplier network calls.

```text
Catalog read
→ Supplier network calls
→ in-memory normalization/domain calculation
→ result
```

Supplier groups are queried concurrently.

Concrete coroutine structure, timeout values, batch sizes, connection-pool settings, and concurrency limits belong outside this document.

---

# 7. Guardrails

```text
Startup trigger             ≠ Catalog Use Case
Catalog baseline/readiness  ≠ Search domain state
Known empty Catalog         ≠ unknown Catalog state
Supplier batching          ≠ Search domain rule
Price protocol translation ≠ Search domain rule
Availability calculation   ≠ Supplier protocol rule
Published Read Contract    ≠ Catalog Aggregate
Supplier result item       ≠ internal Catalog identity
StayOffer                  ≠ API response DTO
Search Unavailable         ≠ HTTP 503
```

Create a new Use Case only when a new application capability introduces a distinct actor/consumer,
input/output contract, orchestration flow, or transaction boundary.

Do not create a Use Case merely to wrap one repository call, domain method, mapper, or Supplier client operation.

---

# 8. Requirement Trace

```text
UC-CAT-01
→ CAT
→ Catalog-side CON
→ catalog synchronization decisions

Q-CAT-01
→ CAT mapping/searchability
→ SEA search-target requirements

UC-SEA-01
→ SEA
→ RES
→ availability-side CON
```

Detailed requirement-to-test traceability belongs in `VERIFICATION.md`.
