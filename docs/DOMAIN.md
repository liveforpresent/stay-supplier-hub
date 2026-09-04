# DOMAIN.md

> Canonical business-domain model.
>
> - Requirements and acceptance criteria: `REQUIREMENTS.md`
> - Canonical terminology: `GLOSSARY.md`
> - Modules, transactions, HTTP, persistence, concurrency: `ARCHITECTURE.md` / ADRs
>
> Keep this file focused on **domain ownership, model boundaries, and invariants**.

---

## 1. Domain Overview

The system integrates accommodation products from multiple Suppliers into two business Bounded Contexts:

- **Catalog** — stable accommodation identity and relatively static product metadata.
- **Search** — live offers for a requested stay period and guest composition.

Supplier-specific protocols are isolated behind an **Anti-Corruption Layer (ACL)** and are not a Bounded Context.

```text
External Supplier A/B
        │
        ▼
Supplier Integration / ACL
   ├─ implements Catalog outbound contract
   └─ implements Search outbound contract
        │
        ▼

Catalog BC ── Published Read Contract ──▶ Search BC
```

---

## 2. Context Ownership

| Concern | Owner |
|---|---|
| Internal Property identity | Catalog |
| Internal RoomType identity | Catalog |
| Supplier ↔ internal identity mapping | Catalog |
| Property / RoomType metadata | Catalog |
| ACTIVE / INACTIVE lifecycle | Catalog |
| SearchCondition / StayPeriod | Search |
| Live whole-stay price | Search |
| Whole-stay availability | Search |
| Offer conditions | Search |
| StayOffer | Search |
| Integrated search outcome semantics | Search |
| Supplier DTO / protocol | Supplier Integration |
| Raw Supplier failure interpretation | Supplier Integration |
| HTTP response/status mapping | Web adapter |

Neither business context owns Supplier-specific HTTP concepts.

---

# 3. Catalog Bounded Context

## 3.1 Responsibility

Catalog answers:

> Which internal accommodation product corresponds to this Supplier product?

Catalog owns stable internal identity and catalog lifecycle. It does not own live rate or inventory.

---

## 3.2 Aggregate Boundary

```text
Property (Aggregate Root)
└── RoomType (Entity)
```

`RoomType` is not an independent Aggregate in the current domain because:

- it exists only within a Property,
- its external identity is scoped to its Property,
- it has no independent command lifecycle,
- live rate/inventory are not stored on it.

Revisit this boundary only if RoomType gains an independent lifecycle, command surface, or concurrency boundary.

---

## 3.3 Property

`Property` is one Supplier-sourced accommodation facility managed in the internal Catalog.

It is **not** a globally canonical real-world building.

### Identity

Internal:

```text
PropertyId
```

External:

```text
SupplierId + SupplierPropertyCode
```

### State

```text
Property
├── PropertyId
├── SupplierPropertyIdentity
├── Name
├── CatalogStatus
└── RoomTypes
```

### Invariants

- `PropertyId` is immutable.
- External Property identity is immutable.
- One external Property identity maps to one stable `PropertyId`.
- Re-synchronizing the same external identity preserves its `PropertyId`.
- Only an ACTIVE Property can be searched.

---

## 3.4 RoomType

`RoomType` is a room category within one Property, not an individual physical room and not a live offer.

### Identity

Internal:

```text
RoomTypeId
```

External:

```text
SupplierId
+ SupplierPropertyCode
+ SupplierRoomTypeCode
```

Within the Aggregate, Supplier and Property identity are inherited from the parent Property.

### State

```text
RoomType
├── RoomTypeId
├── SupplierRoomTypeCode
├── Name
├── MaxOccupancy
└── CatalogStatus
```

### Invariants

- A RoomType belongs to exactly one Property.
- `RoomTypeId` is immutable.
- `SupplierRoomTypeCode` is unique within one Property.
- Re-synchronizing the same external RoomType identity preserves its `RoomTypeId`.
- Only an ACTIVE RoomType under an ACTIVE Property can be searched.

---

## 3.5 Catalog Lifecycle

A successfully fetched and fully validated Supplier catalog is treated as a complete synchronization snapshot.

Reconciliation rules:

| Snapshot state | Catalog action |
|---|---|
| Existing identity | Preserve internal ID, update mutable metadata, activate |
| New identity | Create new internal ID as ACTIVE |
| Existing Property missing | Mark Property INACTIVE |
| Existing RoomType missing from a present Property | Mark RoomType INACTIVE |
| Inactive identity reappears | Reactivate with existing internal ID |
| Snapshot fetch/validation fails | Preserve current state; do not infer absence |

When a Property becomes INACTIVE, child RoomType statuses do not need to be rewritten. Effective searchability is:

```text
Property.ACTIVE && RoomType.ACTIVE
```

Physical deletion is not part of normal reconciliation.

`Property` remains the Aggregate boundary even when catalog synchronization coordinates multiple Properties at the application level.

---

# 4. Search Bounded Context

## 4.1 Responsibility

Search answers:

> For this stay period and guest composition, what Supplier-backed accommodation offers are available now?

Search combines:

1. Catalog identity/reference data,
2. live Supplier data,
3. Supplier-independent search rules.

Search currently has **no persistent Aggregate Root**. Search values and offers are transient.

---

## 4.2 Search Model

```text
SearchCondition
├── StayPeriod
└── GuestComposition

StayOffer
├── PropertyId / PropertyName
├── RoomTypeId / RoomTypeName / MaxOccupancy
├── SupplierId
├── StayPrice
├── StayAvailability
└── OfferConditions
```

Primary domain values:

| Model | Meaning |
|---|---|
| `SearchCondition` | Complete input for the current search capability |
| `StayPeriod` | `[checkIn, checkOut)` accommodation period |
| `GuestComposition` | Adults and children preserved separately |
| `DailyInventory` | Remaining rooms for one stay date |
| `StayAvailability` | Rooms available for the entire StayPeriod |
| `Money` | Amount with currency |
| `StayPrice` | Total customer-facing price for the entire StayPeriod |
| `OfferConditions` | Commercial conditions such as breakfast inclusion |
| `StayOffer` | Immutable live offer produced for one search |

---

## 4.3 StayPeriod

Invariant:

```text
CheckIn < CheckOut
```

Stay dates use a half-open interval:

```text
[CheckIn, CheckOut)
```

Example:

```text
2026-09-01 → 2026-09-04
= 09-01, 09-02, 09-03
= 3 nights
```

The checkout date is excluded.

---

## 4.4 GuestComposition

Adults and children are distinct values and must remain distinct through Supplier requests.

`MaxOccupancy` is RoomType capacity and must not be confused with room inventory.

---

## 4.5 StayAvailability

`DailyInventory` represents one date:

```text
Date + RemainingRooms
```

`RemainingRooms` must be non-negative.

For the complete StayPeriod:

```text
AvailableRooms =
    min(RemainingRooms for every required stay date)
```

A valid availability calculation requires exactly one inventory value for every required stay date.

Invalid inputs include:

- missing required date,
- duplicate date,
- date outside the StayPeriod,
- negative inventory.

If any required date has zero inventory:

```text
AvailableRooms = 0
```

Zero inventory is a valid commercial result, not malformed data.

The current policy keeps valid offers with `AvailableRooms = 0` in search results.

---

## 4.6 StayPrice

`StayPrice` models the **common business meaning**, not the richest Supplier price structure.

```text
Supplier A nightly net/tax details
Supplier B whole-stay gross total
        ↓ Integration normalization
whole-stay customer-facing total + currency
        ↓ Search
StayPrice
```

Supplier A's nightly price/tax detail is intentionally not retained after a correct whole-stay total is derived.
Supplier B has no equivalent nightly breakdown, so Search never fabricates one merely to force structural symmetry.

`Money` consists of:

```text
Amount + Currency
```

`StayPrice` is the total customer-facing price for the complete StayPeriod.

Rules:

- currency is always preserved,
- implicit FX conversion is not performed,
- Search does not invent a common nightly/tax breakdown,
- Supplier-specific price structures are translated before entering Search domain logic.

---

## 4.7 OfferConditions

Current model:

```text
OfferConditions
└── BreakfastIncluded
```

`BreakfastIncluded` belongs to a live offer, not to RoomType metadata, because conditions may differ by Supplier or offer.

---

## 4.7.1 Information Authority

Search combines information from two authorities:

```text
Catalog
→ internal Property/RoomType identity
→ Property/RoomType names
→ MaxOccupancy

Supplier live response
→ whole-stay price
→ daily inventory input
→ breakfast condition
→ Supplier source
```

Availability/search responses may repeat names or occupancy. Those repeated values do not become authoritative
Catalog metadata. External Supplier codes are mapping/join keys, not public Search identity.

---

## 4.8 StayOffer

`StayOffer` is the central Search domain result.

It is:

- transient,
- immutable after construction,
- not persisted,
- not an Entity,
- not an Aggregate Root,
- not an HTTP response DTO.

Key distinction:

```text
RoomType ≠ StayOffer
```

`RoomType` is stable catalog identity.  
`StayOffer` is a live commercial representation for one search.

---

# 5. Context Contracts

## 5.1 Catalog → Search Published Read Contract

Search must not read Catalog repositories, persistence entities, or Aggregate internals directly.

Catalog exposes only the data required by Search, conceptually:

```text
Property
├── internal PropertyId
├── Property name
├── SupplierId
├── SupplierPropertyCode
└── RoomTypes[]
    ├── internal RoomTypeId
    ├── SupplierRoomTypeCode
    ├── RoomType name
    └── MaxOccupancy
```

This is a **Context-to-Context read contract**, not:

- a Catalog Aggregate,
- a Search Aggregate,
- a View Model,
- an HTTP API model.

Concrete interface/type/module names are defined in architecture design, not here.

---

## 5.2 Catalog → Supplier Integration

Catalog owns the outbound contract needed to fetch Supplier catalog data.

Supplier adapters translate Supplier-specific catalog DTOs into the neutral data required by Catalog.

---

## 5.3 Search → Supplier Integration

Search owns the outbound contract needed to obtain live price and inventory.

Supplier adapters translate Supplier-specific search responses into neutral values required by Search.

The business contexts must never inspect Supplier-specific DTOs or protocol result codes.

---

# 6. Supplier Integration Boundary

Supplier Integration is an ACL / adapter boundary, not a business domain.

Supplier-specific concerns include:

- endpoint paths,
- API authentication,
- request/response DTOs,
- HTTP status semantics,
- body-level result codes,
- transport timeout behavior,
- Supplier-specific price representation.

The boundary translates external concepts into contracts owned by Catalog or Search.

Business code must not contain Supplier-protocol branching such as:

```text
if Supplier A -> inspect A-specific tax fields
if Supplier B -> inspect B-specific resultCode
```

Search-level decisions about whether usable integrated results remain are separate from raw Supplier failure interpretation.

---

# 7. Cross-Supplier Identity

The current domain does not model a canonical real-world Property shared across Suppliers.

Therefore two Supplier products may have different internal `PropertyId` values even when they represent the same real-world accommodation.

Automatic merging based on name, address, price, room name, or similarity is not performed.

Cross-Supplier canonicalization is a separate future capability.

---

# 8. Domain Scope & Evolution

The design covers the broader **accommodation-integration problem space**, while implementation remains focused
on the current core flow.

Do not interpret this section as pre-creating future Bounded Contexts or Aggregates. Adjacent capabilities are
documented to make the current scope deliberate and to identify the concrete requirement that would justify
revisiting a boundary.

## 8.1 Current Core

Current business Bounded Contexts:

```text
Catalog BC
├── Supplier-backed Property / RoomType identity
├── Supplier ↔ internal mapping
├── stable metadata
└── lifecycle / synchronization

Search BC
├── SearchCondition
├── StayPeriod / GuestComposition
├── StayPrice
├── StayAvailability
├── StayOffer
└── integrated Search outcome
```

Supporting boundary:

```text
Supplier Integration
→ ACL / outbound adapter boundary
→ not a Bounded Context
```

Current implementation and architecture are intentionally centered on:

```text
Supplier Catalog
→ internal Catalog mapping
→ searchable Catalog projection
→ live Supplier price/inventory lookup
→ normalization
→ integrated Search result
```

## 8.2 Adjacent Domain Capabilities

These are relevant to the broader problem space but are intentionally **not modeled as current Bounded Contexts
or tactical domain objects**.

### Cross-Supplier Canonicalization

Current behavior:

```text
Supplier A Property → internal PropertyId A
Supplier B Property → internal PropertyId B
```

Even if both represent the same real-world accommodation, they remain independent.

Reason:

- the current source data provides no reliable cross-Supplier canonical key,
- automatic name/address/price similarity matching would introduce uncertain identity semantics,
- the core Search flow does not require a platform-wide real-world Property identity.

Boundary trigger:

> Revisit this area when the product requires one platform Property identity shared across multiple Suppliers.

Likely impact:

- introduce a distinct canonical Property identity concept,
- define explicit Supplier-Property membership/mapping semantics,
- reconsider Catalog ownership and the Catalog → Search Published Contract.

Do not assume today that the future solution must be a separate Bounded Context; defer that decision until the
identity and lifecycle requirements are concrete.

### Reservation / Booking

Current behavior:

- Search ends at live offer discovery.
- No Reservation identity, booking lifecycle, Supplier booking reference, or cancellation state is modeled.

Reason:

- the current core requirement does not create or cancel bookings,
- reservation introduces a different lifecycle and consistency problem from Search.

Boundary trigger:

> Revisit when Supplier booking creation/cancellation becomes a requirement.

Expected concerns at that time:

```text
Reservation identity
status/lifecycle
Supplier booking reference
idempotency
failure handling
cancellation
compensation
```

These concerns are strong signals that a separate business boundary may be warranted, but the exact Aggregate or
Bounded Context is intentionally not fixed now.

### Currency Conversion

Current behavior:

```text
Supplier currency is preserved
implicit FX conversion is not performed
```

Reason:

- the current Search contract does not require cross-currency comparison in one display currency,
- conversion requires an external FX authority and explicit business policy.

Boundary trigger:

> Revisit when customers must compare, sort, or display offers in a common currency.

Expected concerns:

```text
FX-rate source
effective timestamp
base/display currency
rounding policy
staleness
auditability
```

Currency conversion must not silently become a `Money` value-object responsibility without these policies.

## 8.3 Infrastructure / Integration Evolution

These are technical or operational capabilities, not current business Bounded Contexts.

### Retry / Circuit Breaker

Current:

- not required for the core flow,
- timeout, bounded concurrency, failure normalization, and partial failure are implemented first.

Future trigger:

- measured transient failure patterns or repeated Supplier instability justify controlled retry/backoff or circuit breaking.

Placement:

- Supplier Integration / infrastructure,
- not a new domain context.

### Price / Inventory Cache

Current:

- no live price/inventory cache,
- Suppliers remain authoritative at Search time.

Future trigger:

- latency, cost, or Supplier rate limits justify caching.

Required decisions before implementation:

```text
TTL
acceptable staleness
stampede protection
invalidation
fallback semantics
```

Cache remains an infrastructure optimization unless the business defines a domain-level staleness policy.

### Normalization Quarantine

Current:

- invalid Supplier payloads are isolated through typed failures, logs, and metrics,
- malformed responses are not persistently stored.

Future trigger:

- operations require replay, manual analysis, or persistent evidence of malformed Supplier data.

Possible evolution:

```text
quarantine storage
diagnostic metadata
replay / analysis tooling
```

This is an operational integration capability and does not justify a business Bounded Context by itself.

## 8.4 Explicitly Unmodeled Product Concepts

### Individual Physical Room

Not modeled because the current Supplier contracts expose room-category inventory counts, not individual physical
room identity or lifecycle.

```text
RoomType inventory count
≠
individual physical Room
```

### Persistent RatePlan

Not modeled because the current Supplier contracts do not provide a stable RatePlan identity or independent
RatePlan lifecycle.

Search-dependent commercial conditions such as price and breakfast inclusion remain part of `StayOffer`.

Boundary trigger:

> Revisit only when Suppliers expose stable sellable plans with independent identity, policy, or lifecycle.

### Persistent Search History

Not modeled because current Search is a transient read capability and no business requirement depends on retaining
past Search executions.

## 8.5 Explicit Out of Scope

The following remain outside the current project scope and must not become dependencies of the core Search flow:

- end-user authentication / authorization,
- payment integration,
- administrator product features,
- frontend implementation,
- real commercial Supplier API integration,
- region / keyword search,
- sorting / pagination.

`Reservation / Booking` is an adjacent optional evolution area, while `payment integration` remains explicitly
out of scope. Do not collapse the two.

## 8.6 Scope Principle

```text
broader domain awareness
≠ speculative tactical modeling
```

Add a new Aggregate, Entity, Value Object, or Bounded Context only when a concrete requirement introduces a new
identity, lifecycle, consistency boundary, or domain meaning.

Document likely boundary triggers now; defer tactical structure until those requirements exist.

---

# 9. Domain Guardrails

```text
Property                  ≠ globally canonical building
RoomType                  ≠ physical Room
RoomType                  ≠ StayOffer
Supplier code             ≠ internal ID
Inventory                 ≠ whole-stay Availability
MaxOccupancy              ≠ AvailableRooms
Catalog model             ≠ Search model
Published Contract        ≠ View Model
StayOffer                 ≠ API response DTO
Supplier Integration      ≠ Bounded Context
Zero inventory            ≠ Supplier failure
```

Do not add a new Aggregate, Entity, or abstraction for hypothetical future requirements. Add one when a real requirement introduces a new identity, lifecycle, consistency boundary, or domain meaning.
