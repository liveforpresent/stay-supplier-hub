# GLOSSARY.md

> Canonical terminology.
>
> Use these terms consistently in code, tests, documentation, and AI-assisted work.
> Detailed ownership and invariants belong in `DOMAIN.md`.

---

## 1. Accommodation & Catalog

| Term | Canonical meaning |
|---|---|
| **Property** | One Supplier-sourced accommodation facility managed in the internal Catalog. Not a globally canonical real-world building. |
| **PropertyId** | Stable internal identifier for a Property. |
| **SupplierPropertyCode** | Supplier-controlled Property identifier. Meaningful together with `SupplierId`. |
| **SupplierPropertyIdentity** | `SupplierId + SupplierPropertyCode`. External identity of one Supplier Property. |
| **RoomType** | Room category within a Property, e.g. Deluxe Twin. Not an individual physical room or live offer. |
| **RoomTypeId** | Stable internal identifier for a RoomType. |
| **SupplierRoomTypeCode** | Supplier-controlled RoomType code scoped to one Property. |
| **Catalog** | BC owning Property/RoomType identity, mappings, metadata, lifecycle, and catalog synchronization. |
| **CatalogStatus** | Catalog lifecycle state: `ACTIVE` or `INACTIVE`. |
| **Catalog Snapshot** | Complete Supplier catalog input used for reconciliation. Transient; not an Aggregate. |
| **Catalog Synchronization** | Reconciliation of a valid Supplier catalog snapshot with current Catalog state. |
| **Catalog Baseline** | Established Catalog knowledge for one configured Supplier. Current startup sync success or previously persisted Catalog state can provide a usable baseline. |
| **Known Empty Catalog** | A Catalog whose baseline is established but currently has zero searchable Properties. Distinct from unknown Catalog state caused by bootstrap failure. |
| **Search Readiness** | Operational state indicating whether Search traffic may be accepted. Requires a usable Catalog baseline for every configured Supplier. Not a Search domain state. |
| **Supplier** | External accommodation product provider. |
| **SupplierId** | Internal, type-safe identifier for an integrated Supplier. Represented as a non-blank string-backed value object, not a closed A/B enum. Expressible Supplier IDs and currently supported Supplier integrations are separate concepts. |

Preferred internal accommodation term: **Property**.  
Use Supplier terms such as `hotel` only inside Supplier-specific adapter/DTO code when the external protocol uses them.

---

## 2. Search & Offer

| Term | Canonical meaning |
|---|---|
| **Search** | BC producing unified live accommodation offers for a stay request. Not a UI/View layer. |
| **SearchCondition** | Search input composed of `StayPeriod` and `GuestComposition`. |
| **StayPeriod** | Accommodation interval `[CheckIn, CheckOut)`. Checkout is excluded. |
| **GuestComposition** | Adults and children preserved as separate values. |
| **MaxOccupancy** | Maximum occupants supported by one RoomType. |
| **Inventory** | Date-specific remaining room quantity from a Supplier. |
| **DailyInventory** | Inventory for exactly one date. |
| **RemainingRooms** | Raw remaining quantity for one date. |
| **StayAvailability** | Availability for the entire StayPeriod. Derived from all required daily inventory. |
| **AvailableRooms** | Rooms able to satisfy the entire StayPeriod; minimum of required daily inventory. |
| **Money** | Amount paired with currency. |
| **StayPrice** | Total customer-facing price for the complete StayPeriod. |
| **OfferConditions** | Commercial conditions attached to one StayOffer. |
| **BreakfastIncluded** | Whether breakfast is included in a specific StayOffer. Not stable RoomType metadata. |
| **StayOffer** | Immutable, transient Search result combining catalog identity, live price, availability, conditions, and Supplier source. |

---

## 3. Boundaries & DDD

| Term | Canonical meaning |
|---|---|
| **Bounded Context (BC)** | Boundary in which a domain model and language have one consistent meaning. Current BCs: Catalog and Search. |
| **Published Contract** | Contract intentionally exposed by one BC for another BC to consume. May be Kotlin interfaces/data, not necessarily HTTP. |
| **Published Read Contract** | Catalog-owned read contract through which Search obtains only search-relevant catalog data. |
| **Anti-Corruption Layer (ACL)** | Translation boundary preventing Supplier protocol/models from leaking into internal business models. |
| **Supplier Integration** | ACL/outbound adapter boundary for external Suppliers. Not a BC. |
| **Aggregate** | Transactional consistency boundary for domain objects and invariants. |
| **Aggregate Root** | Entity controlling changes within an Aggregate. Current root: `Property`. |
| **Entity** | Domain object with stable identity. Current child Entity: `RoomType`. |
| **Value Object** | Immutable domain value defined by value/invariants rather than independent lifecycle identity. |
| **API Response DTO** | HTTP transport representation. Not the same as a Domain model or Published Contract. |
| **View Model** | Presentation-specific model. Do not use this term for BC read contracts. |

---

## 4. Failure & Availability Terms

| Term | Canonical meaning |
|---|---|
| **Supplier Failure** | Supplier interaction failure after Supplier-specific protocol interpretation. |
| **Partial Success** | Usable search results remain although one or more Supplier/batch interactions failed. |
| **Search Unavailable** | No Supplier source produced a usable integrated search result because all relevant processing failed. Not an HTTP status itself. |
| **Invalid Response** | Supplier response cannot be safely normalized into the required internal contract. |
| **Zero Inventory** | Valid result with `AvailableRooms = 0`. Not a failure or malformed response. |

Raw Supplier codes/statuses are integration terminology, not business-domain terminology.

---

## 5. Canonical Distinctions

```text
Property             ≠ physical building identity
Property             ≠ cross-Supplier canonical accommodation

RoomType             ≠ individual Room
RoomType             ≠ StayOffer

PropertyId           ≠ SupplierPropertyCode
RoomTypeId           ≠ SupplierRoomTypeCode

Inventory            ≠ StayAvailability
MaxOccupancy         ≠ AvailableRooms

Catalog              ≠ Search
Catalog Snapshot     ≠ Catalog Aggregate

StayOffer            ≠ API Response DTO
Published Contract   ≠ View Model
Supplier Integration ≠ Bounded Context

Zero Inventory       ≠ Supplier Failure
Search Unavailable   ≠ HTTP 503
```

---

## 6. Naming Rules

| Prefer | Avoid / restrict |
|---|---|
| `Property` | `Hotel` outside Supplier-specific adapters |
| `RoomType` | `Room` when referring to a room category |
| `StayOffer` | generic `Product`, `SearchItem` |
| `StayPeriod` | generic `DateRange` when stay semantics matter |
| `GuestComposition` | collapsed guest count when adults/children must remain distinct |
| `RemainingRooms` | `AvailableRooms` for one-day raw inventory |
| `AvailableRooms` | `Stock` for whole-stay availability |
| `SupplierPropertyCode` | generic `ExternalId` |
| `SupplierRoomTypeCode` | generic `ExternalRoomId` |
| `Published Contract` | `ViewModel` |
| `Supplier Integration` / `ACL` | `Integration BC` |

Do not add terminology for speculative future models. Add a term when it becomes part of the actual domain or is needed to prevent ambiguity.
