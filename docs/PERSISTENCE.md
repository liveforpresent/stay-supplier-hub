# PERSISTENCE.md

> Canonical Catalog persistence specification.
>
> - Domain: `DOMAIN.md`
> - Use cases: `USE_CASES.md`
> - Architecture: `ARCHITECTURE.md`
> - Supplier semantics: `INTEGRATION.md`
>
> Defines **Catalog persistence ownership, Snowflake IDs, relational constraints, JPA mapping,
> reconciliation persistence, Search projection, migrations, and persistence verification**.

---

## 1. Scope

PostgreSQL persists **Catalog state only**.

Persist:

```text
Property / RoomType internal identity
Supplier ↔ internal identity mapping
PropertyName / RoomTypeName / MaxOccupancy
CatalogStatus
createdAt / updatedAt
```

Do not persist as core behavior:

```text
StayOffer
StayPrice
DailyInventory
StayAvailability
BreakfastIncluded
Supplier availability raw response
Search history/result
```

Price/inventory remain live Supplier data.

---

# 2. Internal ID Strategy

```text
PropertyId → Snowflake-backed positive Long
RoomTypeId → Snowflake-backed positive Long
DB         → BIGINT
```

Do not use:

```text
BIGSERIAL
IDENTITY
database auto-increment
```

IDs exist before Domain object persistence.

```text
new external identity
→ Catalog ID Generator Port
→ Snowflake-backed ID
→ Domain object
→ Repository
```

Catalog owns:

```text
PropertyIdGenerator
RoomTypeIdGenerator
```

`:shared:infrastructure` owns only the domain-neutral Snowflake mechanism:

```text
SnowflakeIdGenerator → Long
```

`:app` Composition Root binds that mechanism to Catalog-owned Ports:

```text
SnowflakeIdGenerator
→ PropertyIdGenerator { PropertyId(nextId()) }

SnowflakeIdGenerator
→ RoomTypeIdGenerator { RoomTypeId(nextId()) }
```

`shared:infrastructure` must not depend on Catalog or know `PropertyId` / `RoomTypeId`.

Snowflake policy:

```text
positive Long
thread-safe
one process-local generator state shared by Catalog ID bindings
nodeId externalized
clock rollback detected/fail-fast
duplicates never silently tolerated
```

Bit layout/epoch remain implementation details unless fixed by ADR.

---

# 3. Relational Model

```text
properties
└── room_types
```

The relational structure reflects Aggregate ownership but does not replace the Domain model.

---

## 3.1 `properties`

```text
id                       BIGINT       PK
supplier_id              VARCHAR      NOT NULL
supplier_property_code   VARCHAR      NOT NULL
name                     VARCHAR      NOT NULL
status                   VARCHAR      NOT NULL
created_at               TIMESTAMPTZ  NOT NULL
updated_at               TIMESTAMPTZ  NOT NULL
```

Constraint:

```text
UNIQUE(supplier_id, supplier_property_code)
```

Meaning:

```text
SupplierId + SupplierPropertyCode
→ one stable PropertyId
```

---

## 3.2 `room_types`

```text
id                         BIGINT       PK
property_id                BIGINT       NOT NULL FK
supplier_room_type_code    VARCHAR      NOT NULL
name                       VARCHAR      NOT NULL
max_occupancy              INTEGER      NOT NULL
status                     VARCHAR      NOT NULL
created_at                 TIMESTAMPTZ  NOT NULL
updated_at                 TIMESTAMPTZ  NOT NULL
```

Constraints:

```text
FOREIGN KEY(property_id)
REFERENCES properties(id)

UNIQUE(property_id, supplier_room_type_code)
```

Do not duplicate on `room_types`:

```text
supplier_id
supplier_property_code
```

The parent Property already owns those values.

---

# 4. String Representation

Persist these as `VARCHAR`:

```text
SupplierId
CatalogStatus
```

`SupplierId` is a string-backed value object in the published/application model, while persistence stores its
scalar value. Do not introduce a PostgreSQL enum or another DB-level closed list of Supplier A/B values.

Conceptually:

```text
SupplierId("A") ↔ "A"
SupplierId("B") ↔ "B"
```

JPA entities may keep the persisted representation as `String`; the persistence mapper reconstructs
`SupplierId` at the boundary. A custom JPA converter is not required merely to preserve the value-object type.

Examples:

```text
A
B

ACTIVE
INACTIVE
```

Do not use PostgreSQL native enums initially.

This keeps Supplier extension and schema migration simple. Adding Supplier C should not require a schema
migration solely to extend a database enum.

---

# 5. Audit / Locking

Store persistence-only audit timestamps:

```text
created_at
updated_at
```

Policy:

```text
PostgreSQL type → TIMESTAMPTZ
Kotlin type     → Instant
ownership       → persistence layer, not Domain
```

Use persistence auditing/entity callbacks consistently; Domain objects do not carry audit timestamps unless
a future business requirement gives them domain meaning.

Do not add initially:

```text
deleted_at
last_synced_at
@Version
```

`INACTIVE` already expresses Catalog lifecycle.

There is currently one controlled Catalog command path: Supplier catalog synchronization.
Add locking only if concurrent writers become a real requirement.

---

# 6. Domain / JPA Separation

Keep separate:

```text
Domain
├── Property
└── RoomType

Persistence
├── PropertyJpaEntity
└── RoomTypeJpaEntity
```

Domain must not contain:

```text
@Entity
@Table
@OneToMany
@ManyToOne
Hibernate collection/lazy-loading behavior
```

Persistence adapters own Domain ↔ JPA mapping.

---

# 7. JPA Relationship Policy

Conceptually:

```text
PropertyJpaEntity
└── RoomTypeJpaEntity[]
```

Recommended:

```text
cascade → PERSIST, MERGE
cascade REMOVE → no
orphanRemoval  → false
DB FK delete   → RESTRICT / NO ACTION
```

Physical removal of a parent must not implicitly erase RoomType history.

Missing Supplier data means:

```text
INACTIVE
```

not physical deletion.

---

# 8. Repository Contracts

Command-side Port:

```text
PropertyRepository
```

Required conceptual operations:

```text
findAllBySupplier(SupplierId)
hasPersistedCatalogState(SupplierId)
saveAll(Collection<Property>)
```

`hasPersistedCatalogState` is a narrow existence check used by startup/bootstrap coordination when the current
Supplier synchronization fails. It answers whether any previously persisted Catalog state exists for that
Supplier, including INACTIVE state. It must not mean "has searchable Properties".

```text
hasPersistedCatalogState = true
≠ at least one ACTIVE/searchable Property
```

A valid current startup synchronization establishes a baseline independently of this existence check, including
when the valid snapshot is empty.

For the current scope, no separate synchronization-metadata table is introduced. Consequently, a Supplier whose
historically successful Catalog has always been truly empty cannot be recognized as having a previous baseline
after a later process restart if the next synchronization also fails.

`findAllBySupplier` is a **reconciliation read**, not a searchable read. It must rehydrate the complete
Supplier Catalog state required by the Aggregate:

```text
ACTIVE + INACTIVE Properties
ACTIVE + INACTIVE RoomTypes
```

Filtering inactive state here would break stable-ID reactivation and missing-item reconciliation.

`saveAll` persists complete Property Aggregate state. Do not introduce an independent RoomType command
repository while RoomType remains an Entity inside Property.

The persistence adapter must fetch the RoomType state needed for each Property reconciliation without an
N+1 query pattern. A fetch join, entity graph, or equivalent bounded query strategy is acceptable; the
specific JPA mechanism is an adapter implementation detail.

Add operations only when a real use case requires them.

The bootstrap existence check is such a case, but it must remain an efficient presence query rather than loading
all Aggregates only to determine whether previous state exists.

Do not expose through Port:

```text
JpaRepository
EntityManager
JPA entity types
```

---

# 9. Searchable Catalog Reader

Search must not load Catalog Aggregates.

Persistence query-side Port:

```text
SearchableCatalogReader
```

It supports `ReadSearchableCatalog` and returns persistence-neutral projection data.

Public Context-to-Context contract remains in:

```text
:catalog:api
```

---

# 10. Search Projection

Required fields:

```text
PropertyId
PropertyName
SupplierId
SupplierPropertyCode

RoomTypeId
RoomTypeName
SupplierRoomTypeCode
MaxOccupancy
```

Conceptual query:

```sql
SELECT
    p.id,
    p.name,
    p.supplier_id,
    p.supplier_property_code,
    r.id,
    r.name,
    r.supplier_room_type_code,
    r.max_occupancy
FROM properties p
JOIN room_types r
  ON r.property_id = p.id
WHERE p.status = 'ACTIVE'
  AND r.status = 'ACTIVE'
```

Result is grouped into:

```text
SearchableProperty
└── SearchableRoomType[]
```

Preferred implementation:

```text
Spring Data JPA / JPQL projection
```

Use native SQL only if necessary.

Do not use a PostgreSQL materialized view for the current workload.

---

# 11. Snapshot Reconciliation Transaction

Only a successfully fetched and fully validated Supplier snapshot may be persisted.

```text
Supplier HTTP / normalization
        ↓
validated complete snapshot
        ↓
begin transaction
        ↓
load existing Supplier Catalog
        ↓
reconcile
        ↓
persist
        ↓
commit
```

Supplier HTTP never runs inside the DB transaction.

One Supplier snapshot is one application-level transaction.

A/B transactions are independent.

---

## 11.1 Property Reconciliation

```text
existing identity
→ preserve PropertyId
→ update mutable metadata
→ ACTIVE

new identity
→ PropertyIdGenerator.next()
→ create ACTIVE

existing but missing
→ INACTIVE

inactive then reappears
→ same PropertyId
→ ACTIVE
```

---

## 11.2 RoomType Reconciliation

Within each present Property:

```text
existing code
→ preserve RoomTypeId
→ update mutable metadata
→ ACTIVE

new code
→ RoomTypeIdGenerator.next()
→ create ACTIVE

existing but missing
→ INACTIVE

inactive then reappears
→ same RoomTypeId
→ ACTIVE
```

RoomType changes occur through the Property Aggregate boundary.

---

# 12. Failure / Empty Snapshot Rules

If Supplier fetch or snapshot validation fails:

```text
do not reconcile
do not infer absence
do not deactivate previous data
```

A valid complete empty snapshot is legitimate:

```text
Success(empty snapshot)
→ all existing Properties for that Supplier become INACTIVE
```

Do not add speculative minimum-size safeguards.

---

# 13. Atomicity

Persistence failure during reconciliation:

```text
→ rollback entire Supplier snapshot
```

Partial Catalog reconciliation is not allowed.

This transaction may coordinate multiple Property Aggregates intentionally.

---

# 14. Stable Identity Defense

Application/Domain:

```text
same external identity
→ preserve internal ID
```

Database:

```text
UNIQUE(supplier_id, supplier_property_code)
UNIQUE(property_id, supplier_room_type_code)
```

DB constraints are final integrity defense, not a replacement for reconciliation logic.

---

# 15. Indexes

Primary/unique constraints provide core lookup indexes.

Initial additional indexes:

```text
properties(supplier_id, status)
room_types(property_id, status)
```

Do not add speculative indexes without an observed query need.

---

# 16. Schema Migration

Use Flyway.

```text
db/migration/
└── V1__create_catalog_tables.sql
```

Hibernate schema policy:

```text
ddl-auto=validate
```

Do not use normal runtime:

```text
ddl-auto=create
ddl-auto=update
```

Schema changes must be explicit and version-controlled.

---

# 17. Persistence Configuration

Externalize:

```text
JDBC URL
DB username
DB password
Snowflake nodeId
```

Credentials must not be committed.

Persistence configuration must not leak into Domain/Application models.

---

# 18. Write Optimization

Correctness precedes optimization.

Do not require initially:

```text
Hibernate JDBC batching
custom bulk SQL
COPY
manual flush/clear tuning
```

Measure Catalog synchronization before adding them.

---

# 19. Persistence Verification

Use real PostgreSQL via Testcontainers.

Verify:

```text
Property external identity uniqueness
RoomType identity uniqueness within Property
foreign-key integrity

Domain ↔ JPA round trip

ACTIVE / INACTIVE persistence

SearchableCatalogReader
→ ACTIVE Property + ACTIVE RoomType only

cross-module synchronization transaction failure
→ full rollback

repeated synchronization
→ stable IDs

inactive → reappear
→ same IDs
```

Do not substitute H2 for PostgreSQL persistence integration tests.

---

# 20. Snowflake Verification

Test `:shared:infrastructure` separately:

```text
ID > 0
sequential uniqueness
concurrent uniqueness
nodeId validation
clock rollback handling
```

Catalog tests verify semantic behavior:

```text
new identity gets an ID
same identity keeps the ID
```

They do not verify Snowflake bit arithmetic.

---

# 21. Test Ownership

```text
Catalog Domain
→ reconciliation invariants

Catalog Application
→ synchronization behavior with fake Ports/repositories

Catalog Persistence Adapter
→ JPA mapping / constraints / projection using PostgreSQL

App / Cross-module Integration
→ real Catalog application transaction + persistence adapter rollback

Shared Infrastructure
→ Snowflake mechanism correctness
```

Test each rule at the lowest layer that owns it.

---

# 22. Guardrails

```text
PostgreSQL persists Catalog only

live Supplier price/inventory are not persisted

PropertyId / RoomTypeId are Snowflake-backed BIGINT

DB never generates Catalog IDs

Supplier external identity is unique

RoomType external code is scoped by Property

RoomType disappearance means INACTIVE, not DELETE

JPA Entity ≠ Domain Entity

Search reads projection, not Aggregate

Supplier HTTP never runs inside Catalog transaction

failed snapshot never causes absence inference

validated Supplier snapshot is atomic

Flyway owns schema

PostgreSQL Testcontainers verify persistence
```

---

# 23. Decision Summary

| Concern | Decision |
|---|---|
| Persistent BC | Catalog only |
| Database | PostgreSQL |
| Property ID | Snowflake `Long` / `BIGINT` |
| RoomType ID | Snowflake `Long` / `BIGINT` |
| DB-generated IDs | No |
| Snowflake mechanism | `:shared:infrastructure`, domain-neutral |
| ID Generator Ports | Catalog-owned |
| Snowflake → Catalog ID binding | `:app` Composition Root |
| Tables | `properties`, `room_types` |
| Property unique | `(supplier_id, supplier_property_code)` |
| RoomType unique | `(property_id, supplier_room_type_code)` |
| Duplicate Supplier columns on RoomType | No |
| Supplier/status storage | `VARCHAR` |
| Domain/JPA model | Separate |
| Audit timestamps | `TIMESTAMPTZ` / persistence-owned `Instant` |
| JPA child cascade | `PERSIST`, `MERGE` only |
| `orphanRemoval` | No |
| `@Version` | No initially |
| Search persistence | None |
| Search read | Projection |
| Materialized view | No |
| Snapshot atomicity | One Supplier snapshot / transaction |
| Valid empty snapshot | Legitimate complete snapshot |
| Migration | Flyway |
| Hibernate schema mode | `validate` |
| Persistence test DB | PostgreSQL Testcontainers |

---

# 24. Evolution Rules

### Supplier C

No schema change should be required solely to add Supplier C.

### Large Catalog synchronization

Measure first; possible later options:

```text
JDBC batching
bulk operations
staged snapshots
versioned activation
```

### Concurrent Catalog writers

Choose locking only after identifying the actual contention model.

### Search persistence

Add Search tables only when Search gains a real persistent lifecycle/history/cache requirement.

Do not persist live Supplier data merely because PostgreSQL already exists.
