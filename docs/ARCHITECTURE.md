# ARCHITECTURE.md

> Canonical implementation architecture.
>
> - Requirements: `REQUIREMENTS.md`
> - Domain: `DOMAIN.md`
> - Terminology: `GLOSSARY.md`
> - Application flows: `USE_CASES.md`
> - Supplier semantics: `INTEGRATION.md`
>
> Defines **module boundaries, dependency direction, runtime composition, transactions, concurrency,
> persistence/web placement, and architecture guardrails**.
>
> HTTP details belong in `API.md`; test traceability belongs in `VERIFICATION.md`.

---

## 1. Architecture

The system is a **Modular Monolith** with two business Bounded Contexts:

```text
Catalog
Search
```

Supplier Integration is an ACL/outbound-adapter boundary, not a Bounded Context.

Runtime:

```text
Main Spring Boot App
├── Catalog
├── Search
├── Supplier A/B adapters
├── Search HTTP API
└── PostgreSQL

Mock Supplier App
└── separate process / local port
```

One deployable main application is sufficient because Catalog/Search need clear model boundaries,
not independent deployment.

---

## 2. Technology & Programming Model

Baseline:

```text
Kotlin 2.3.21
JDK 25
Spring Boot 4.1.1
Gradle 9.3.x / Kotlin DSL
PostgreSQL
Spring MVC
Kotlin Coroutines
Spring WebClient
```

Primary runtime dependencies:

```text
spring-boot-starter-webmvc
spring-boot-starter-webclient
spring-boot-starter-data-jpa
kotlinx-coroutines-core
kotlinx-coroutines-reactor
Actuator / Micrometer
```

### Decision

```text
Inbound HTTP     → Spring MVC
Supplier HTTP    → WebClient
Async I/O        → suspend functions + structured coroutines
Persistence      → blocking JPA/PostgreSQL
```

Rationale:

- inbound traffic is ordinary request/response,
- persistence is blocking JPA,
- no reactive persistence or full reactive server requirement exists,
- WebClient satisfies asynchronous Supplier I/O,
- coroutines keep concurrent integration logic imperative and structured.

Do not use:

```text
RestClient / RestTemplate for Supplier calls
.block()
GlobalScope
unbounded async fan-out
```

---

# 3. Gradle Modules

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

`:shared:infrastructure` is allowed only for narrowly scoped, domain-neutral technical infrastructure.
It must never become a business/common dumping ground.

---

# 4. Module Responsibilities

| Module | Responsibility |
|---|---|
| `:catalog:api` | Catalog Published Language / Published Read Contract |
| `:catalog:domain` | Property Aggregate, RoomType Entity, Catalog invariants |
| `:catalog:port` | Catalog inbound/outbound Ports |
| `:catalog:application` | Catalog use-case orchestration |
| `:catalog:adapter:persistence` | JPA/PostgreSQL implementation and Catalog projections |
| `:search:domain` | SearchCondition, StayOffer, price/availability domain rules |
| `:search:port` | Search inbound/outbound Ports |
| `:search:application` | Search orchestration and partial-result composition |
| `:search:adapter:web` | Public HTTP adapter |
| `:integration:supplier-a` | Supplier A ACL/client/adapters |
| `:integration:supplier-b` | Supplier B ACL/client/adapters |
| `:shared:infrastructure` | Domain-neutral technical infrastructure such as Snowflake ID generation |
| `:app` | Spring Boot Composition Root / bootstrap |
| `:mock-supplier` | Independent Supplier A/B mock server |

---

# 5. Catalog Architecture

## `:catalog:api`

Stable surface intentionally published to downstream contexts.

Contains conceptually:

```text
PropertyId
RoomTypeId
SupplierId

ReadSearchableCatalog
SearchableProperty
SearchableRoomType
```

`SupplierId` is owned by `:catalog:api` because Supplier provenance is part of the Catalog Published Contract
consumed by Search.

Conceptually:

```kotlin
@JvmInline
value class SupplierId(
    val value: String,
) {
    init {
        require(value.isNotBlank())
    }
}
```

It is intentionally **not** an enum such as `A / B`.

```text
SupplierId expressibility
≠
currently supported Supplier integrations
```

The value object provides semantic type safety. Runtime support is defined by Composition Root registration.

`api` means **Bounded Context public contract**, not HTTP API.

Rules:

```text
no Spring
no JPA
no WebClient
no Aggregate implementation
```

---

## `:catalog:domain`

Contains:

```text
Property
RoomType
CatalogStatus
SupplierPropertyIdentity
SupplierPropertyCode
SupplierRoomTypeCode
```

Dependency:

```text
catalog:domain → catalog:api
```

This allows the Catalog Aggregate to use its own published identity types.

Domain remains framework-free.

---

## `:catalog:port`

Suggested packages:

```text
port.in
port.out.persistence
port.out.supplier
```

Conceptual Ports:

```text
IN
SynchronizeSupplierCatalogUseCase

OUT
SupplierCatalogPort
PropertyRepository
SearchableCatalogReader
PropertyIdGenerator
RoomTypeIdGenerator
```

`PropertyIdGenerator` and `RoomTypeIdGenerator` are Catalog-owned outbound Ports.
They return Catalog identity types and expose no Snowflake-specific concept.

Catalog-owned Supplier contract types also live in `:catalog:port`, for example:

```text
SupplierCatalogSnapshot
CatalogSupplierFailure
CatalogSupplierFailureType
```

Do not place these types in Supplier implementation modules.

`SearchableCatalogReader` supports projection reads used by `ReadSearchableCatalog`.
Search must not load Aggregates just to obtain mappings.

---

## `:catalog:application`

Implements:

```text
SynchronizeSupplierCatalogUseCase
ReadSearchableCatalog
```

Conceptual internal components:

```text
SynchronizeSupplierCatalogService
ApplyCatalogSnapshotService
ReadSearchableCatalogService
```

Responsibilities:

```text
select Supplier Port by SupplierId
fetch neutral snapshot
coordinate reconciliation
own transaction boundary
expose Published Read Contract
```

Must not use Supplier DTOs, WebClient, JPA entities, SQL, or controllers.

---

## `:catalog:adapter:persistence`

Implements:

```text
PropertyRepository
SearchableCatalogReader
```

Contains:

```text
JPA entities
Spring Data repositories
domain ↔ persistence mapping
searchable Catalog projection queries
```

Exact schema/indexes are finalized separately.

---

# 6. Search Architecture

## `:search:domain`

Contains:

```text
SearchCondition
StayPeriod
GuestComposition
DailyInventory
StayAvailability
Money
StayPrice
OfferConditions
StayOffer
```

Intentional dependency:

```text
search:domain → catalog:api
```

Catalog is upstream; Search may use only Catalog's Published Language.

---

## `:search:port`

Suggested packages:

```text
port.in
port.out.supplier
```

Conceptual Ports:

```text
IN
SearchStaysUseCase

OUT
SupplierAvailabilityPort
```

Search-owned Supplier contract types also live in `:search:port`, for example:

```text
SupplierPropertyTarget
SupplierAvailabilityItem
SupplierAvailabilityOutcome
SearchSupplierFailure
SearchSupplierFailureType
```

The identical-looking Catalog/Search failure taxonomy is a semantic convention, not a reason to introduce
a shared runtime contract module. Each consuming BC owns its Port types.

The inbound surface also owns the application result consumed by the web adapter.

---

## `:search:application`

Responsibilities:

```text
read Catalog Published Contract
group targets by Supplier
select Supplier Port by SupplierId
execute Supplier groups concurrently
join external codes to Catalog IDs
apply Search domain rules
construct StayOffer
merge failures
return Result / Unavailable
```

Forbidden dependencies:

```text
catalog:domain
catalog:application
catalog:persistence
integration:supplier-*
WebClient
```

---

## `:search:adapter:web`

Contains:

```text
SearchController
request/response DTOs
input validation
SearchOutcome → HTTP mapping
OpenAPI annotations
```

Calls only `SearchStaysUseCase`.

No Catalog HTTP adapter is required for the core flow.

---

# 7. Supplier Integration Architecture

One module per external Supplier:

```text
:integration:supplier-a
:integration:supplier-b
```

Example:

```text
supplier-a
├── SupplierAClient
├── SupplierACatalogAdapter
├── SupplierAAvailabilityAdapter
├── dto/
├── mapper/
├── failure/
└── config/
```

Use separate Catalog and Availability adapter classes even though they share one Supplier client/configuration.

Allowed dependencies:

```text
supplier-a → catalog:port
supplier-a → search:port

supplier-b → catalog:port
supplier-b → search:port
```

Forbidden:

```text
supplier-* → catalog/search application
supplier-* → persistence/web adapters

catalog/search → supplier-* implementation
```

Adapters depend inward on consumer-owned Ports.

---

# 8. Multi-Supplier Composition

No separate routing module.

`:app` explicitly creates:

```text
Map<SupplierId, SupplierCatalogPort>
Map<SupplierId, SupplierAvailabilityPort>
```

Conceptually:

```text
A → SupplierA...
B → SupplierB...
```

Each Port instance is already bound to exactly one Supplier. Therefore Port operations do **not**
receive `SupplierId` again:

```text
SupplierCatalogPort.fetchCatalog()

SupplierAvailabilityPort.search(
    targets,
    SearchCondition
)
```

Application services select the Port by `SupplierId` and then invoke the Supplier-bound operation.

The maps must be assembled explicitly in Composition Root configuration. Do not rely on Spring's
default `Map<String, Bean>` autowiring behavior, because the required key is the semantic `SupplierId`.

The supported Supplier set is determined here, not by a central enum.

Conceptually:

```text
configured Supplier IDs
        ↓
Composition Root registration
        ↓
Map<SupplierId, SupplierCatalogPort>
Map<SupplierId, SupplierAvailabilityPort>
```

Current bootstrap validates the wiring fail-fast:

```text
configured Supplier IDs
=
Catalog Port keys
=
Availability Port keys
```

A missing or inconsistent binding is an application configuration error and must be detected before Catalog
bootstrap or customer Search execution.

Application services never reference concrete Supplier adapter types.

Adding Supplier C:

```text
add :integration:supplier-c
implement existing Catalog/Search-owned Ports
configure SupplierId("C")
register C adapters in Composition Root
validate wiring
```

Expected unchanged:

```text
SupplierId type
Catalog/Search domain models
consumer-owned Port contracts
Supplier A/B implementations
```

---

# 9. Shared Infrastructure

`:shared:infrastructure` contains only **domain-neutral technical mechanisms**.

Current responsibility:

```text
SnowflakeIdGenerator
SnowflakeProperties
clock / node / sequence handling
```

Ownership is deliberately split:

```text
Technical mechanism
→ :shared:infrastructure
→ SnowflakeIdGenerator produces unique Long values

Business meaning
→ Catalog
→ PropertyId / RoomTypeId
→ PropertyIdGenerator / RoomTypeIdGenerator
```

`:shared:infrastructure` must not know or depend on:

```text
PropertyId
RoomTypeId
Property
RoomType
Catalog Ports
Search
Supplier
```

`:app` Composition Root sees both the technical mechanism and Catalog Ports and binds them explicitly:

```text
SnowflakeIdGenerator
→ PropertyIdGenerator

SnowflakeIdGenerator
→ RoomTypeIdGenerator
```

This binding may be a tiny adapter/bean factory in composition code. It converts `Long` into the appropriate
Catalog identity type but introduces no business rule.

Dependencies:

```text
:app → :shared:infrastructure
:app → :catalog:port

:shared:infrastructure ↛ Catalog/Search
Catalog/Search ↛ :shared:infrastructure
```

Snowflake policy:

```text
internal representation   → positive Long
database representation   → BIGINT
generation timing         → before persistence
nodeId                    → externalized configuration
thread safety             → required
clock rollback            → detect/fail; never silently duplicate
```

Do not use database auto-increment/identity generation for `PropertyId` or `RoomTypeId`.

Guardrail:

> Snowflake generation may be shared. Catalog identity semantics may not.

Do not move Catalog/Search business concepts into this module merely because multiple modules can technically
reference them.

---

# 10. `:app` Composition Root

`:app` is the outermost module and may see all concrete runtime components.

Contains:

```text
@SpringBootApplication
cross-module bean wiring
Supplier Port map assembly
configured Supplier capability validation
runtime configuration
startup Catalog sync trigger
Actuator/Micrometer bootstrap
```

It may decide:

> which implementation satisfies which Port?

It must not decide:

```text
Catalog lifecycle rules
StayAvailability rules
Supplier protocol semantics
partial-result business rules
```

---

# 11. Cross-Context Dependency Rule

The only supported Search → Catalog dependency is:

```text
Search → :catalog:api
```

Forbidden:

```text
search:* → catalog:domain
search:* → catalog:port
search:* → catalog:application
search:* → catalog:adapter:persistence
```

Search consumes:

```text
ReadSearchableCatalog
SearchableProperty
SearchableRoomType
PropertyId
RoomTypeId
SupplierId
```

and nothing else from Catalog.

---

# 12. Framework Boundary

| Layer/module | Spring | JPA | WebClient |
|---|---:|---:|---:|
| `*:api` | No | No | No |
| `*:domain` | No | No | No |
| `*:port` | No | No | No |
| `*:application` | DI/transaction support only | No | No |
| persistence adapter | Yes | Yes | No |
| web adapter | Yes | No | No |
| Supplier adapter | Yes | No | Yes |
| `:shared:infrastructure` | Technical DI/config only | No | No |
| `:app` | Yes | bootstrap only | configuration only |

Framework dependencies must not dictate Domain or Port models.

---

# 13. Catalog Transaction Boundary

Supplier network I/O must occur outside the DB transaction.

```text
SynchronizeSupplierCatalogService          // suspend orchestration
        ↓
SupplierCatalogPort.fetchCatalog()            // suspend network I/O, no DB tx
        ↓
Success(snapshot)
        ↓
ApplyCatalogSnapshotService.apply(...)        // synchronous + transactional
        ↓
load → reconcile → persist → commit
```

`ApplyCatalogSnapshotService` is an internal application collaborator, not a new Use Case.

Because persistence is blocking JPA, the reconciliation transaction is a **non-suspending transactional
method** invoked only after Supplier I/O has completed. Do not wrap WebClient suspension inside a JPA
transaction.

One validated Supplier snapshot is one transaction.

A/B transactions are independent.

This application-level transaction may coordinate multiple `Property` Aggregates intentionally to preserve
one complete Supplier snapshot reconciliation.

---

# 14. Search Transaction Boundary

Never hold a DB transaction while waiting on Supplier HTTP calls.

```text
ReadSearchableCatalog
        ↓
materialize immutable projection
        ↓
DB interaction ends
        ↓
Supplier calls
        ↓
in-memory Search processing
```

The Catalog Published Read Contract must not expose lazy persistence state.

---

# 15. Persistence Boundary

Persist:

```text
Property / RoomType internal identity
Supplier mappings
stable Catalog metadata
CatalogStatus
```

Do not persist as core behavior:

```text
live price
live inventory
StayOffer
search history
raw availability responses
```

Search mappings use a normal projection query.

Do not introduce a PostgreSQL materialized view for the current workload.

---

# 16. Search Concurrency

Two explicit concurrency levels exist.

## Supplier-level

Independent Supplier groups run concurrently using structured concurrency.

```text
Search
├── Supplier A
└── Supplier B
```

Expected external failures become typed outcomes so successful Supplier results survive independent failures.

Prefer supervised structured concurrency for failure isolation.

---

## Batch-level

Inside each Supplier adapter:

```text
targets
→ chunk by Supplier limit
→ bounded concurrent WebClient calls
→ normalize/merge outcomes
```

Current A/B limit:

```text
50 Property codes/request
```

Initial configurable batch concurrency:

```text
5
```

`5` is a conservative tuning default, not a protocol or business constant.

The limiter is owned by the Supplier adapter instance and shared across concurrent Search requests in the same
application instance:

```text
Supplier A Availability Adapter
└── shared batch limiter(5)
    ├── Search #1
    ├── Search #2
    └── Search #3
```

This prevents one application instance from multiplying the configured Supplier concurrency by the number of
simultaneous customer Searches.

The bound is independently configurable per Supplier.

Never create one request/coroutine per Property.
Never create a new Supplier concurrency limiter per Search request.

---

# 17. WebClient Configuration

Use independent Supplier client configuration.

```text
Supplier A WebClient
├── baseUrl
├── X-Api-Key
├── connection timeout
└── response timeout

Supplier B WebClient
└── independently configured
```

Initial local/mock defaults:

```text
connection timeout = 1s
response timeout   = 2s
```

These are **per outbound request** timeouts.

When many batches wait behind the Supplier-global concurrency limiter, the complete Supplier execution may span
multiple waves. Therefore a `2s` response timeout must not be interpreted as a `2s` overall Supplier/Search
latency budget.

The current implementation does not add a separate Supplier-level execution deadline. A production evolution may
add one if measured scale requires a hard upper bound for one Supplier's contribution to integrated Search
latency.

Supplier client/adapters translate failures before returning through Ports.

Do not leak:

```text
WebClient exceptions
Reactor types
raw HTTP errors/resultCode
Supplier DTOs
```

into Catalog/Search.

---

# 18. Startup Catalog Synchronization & Search Readiness

Initial mappings are populated from Supplier catalog APIs.

Catalog bootstrap must distinguish:

```text
known empty Catalog
≠
unknown Catalog because no baseline was established
```

Default startup flow:

```text
application bootstrap
        ↓
validate configured Supplier IDs against registered Catalog/Availability Port maps
        ↓
invoke SynchronizeSupplierCatalogUseCase for each configured Supplier
        ↓
each Supplier sync is independent
        ↓
for each Supplier determine usable Catalog baseline
        │
        ├── current sync success
        │      → baseline available
        │
        ├── current sync failure + persisted Catalog state exists
        │      → stale baseline available
        │
        └── current sync failure + no persisted Catalog state
               → baseline unavailable
        ↓
all configured Suppliers have baseline?
        ├── yes → Search gate OPEN / ACCEPTING_TRAFFIC
        └── no  → Search gate CLOSED / REFUSING_TRAFFIC
```

A failed Supplier sync must not mutate/deactivate its previous Catalog state.

The Search traffic gate is enforced **before** normal `SearchStaysUseCase` execution.
Changing Actuator readiness alone is insufficient because this project may be exercised by calling the HTTP
endpoint directly without an external load balancer or orchestrator.

When the gate is closed:

```text
Search request
→ do not interpret missing Catalog targets as a normal empty Search
→ return public 503 SEARCH_UNAVAILABLE
```

When the gate is open, an established Catalog with zero searchable Properties remains a legitimate empty Search.

Ownership:

```text
Supplier synchronization semantics      → Catalog Application
persisted Catalog-state presence check  → Catalog-owned Port / persistence adapter
bootstrap coordination                  → :app
Search traffic/readiness gate           → :app operational wiring
StayOffer / SearchOutcome               → unchanged
```

The bootstrap coordinator may invoke the existing Catalog synchronization Use Case and a narrow Catalog-owned
state-presence capability. It must not implement reconciliation logic or access JPA directly.

Operational state must not leak into the Search domain:

```text
no SearchOutcome.NOT_READY
no Catalog bootstrap state in StayOffer
no Supplier protocol detail in readiness state
```

For the current implementation, persisted Catalog rows are used to detect a previous baseline. A separate
Supplier synchronization-metadata table is intentionally not introduced. This leaves one accepted edge case:
a historically successful but truly empty Catalog cannot be recovered as a previous baseline after restart if
the next synchronization also fails.

A future scheduler or explicit synchronization trigger may reuse the same synchronization Use Case and reopen
the Search gate after all required baselines become available.

---

# 19. Main Runtime Flows

## Search

```text
HTTP
 ↓
Search readiness/bootstrap gate (:app operational wiring)
 ├── CLOSED → 503 SEARCH_UNAVAILABLE
 └── OPEN
      ↓
SearchController
 ↓
SearchStaysUseCase
 ↓
ReadSearchableCatalog (:catalog:api)
 ↓
group by Supplier
 ↓
A/B SupplierAvailabilityPort calls in parallel
 ↓
Supplier adapters / WebClient / batching
 ↓
normalized outcomes
 ↓
Search domain processing
 ↓
SearchOutcome
 ↓
HTTP DTO/status mapping
```

## Catalog Sync

```text
startup/future trigger
 ↓
SynchronizeSupplierCatalogUseCase(SupplierId)
 ↓
Map<SupplierId, SupplierCatalogPort>
 ↓
Supplier Catalog Adapter
 ↓
neutral complete snapshot
 ↓
transactional reconciliation
 ↓
PostgreSQL
```

---

# 20. Mock Supplier

`:mock-supplier` is a separate executable process.

Contains only the external protocols and required modes:

```text
Supplier A endpoints
Supplier B endpoints

normal
supplier-error
no-response
```

Default local port:

```text
9090
```

Do not depend on production Supplier adapter modules.
Independent mock fixtures better detect accidental protocol drift.

---

# 21. Observability Placement

Supplier-call metrics live near integration adapters:

```text
request count
duration
outcome
timeout
normalization failure
```

Search may record integrated outcome:

```text
complete
partial
unavailable
```

Use only low-cardinality metric tags.

Actuator/Micrometer exposure is assembled by `:app`.

---

# 22. Testing Placement

Tests follow **risk ownership**, not a rule that every class/layer needs the same kind of test.

| Module | Primary test purpose |
|---|---|
| `catalog:domain` | Aggregate invariants and reconciliation behavior as pure unit tests |
| `catalog:application` | Use-case orchestration, stable-ID/lifecycle scenarios with fake Ports |
| `catalog:adapter:persistence` | Real PostgreSQL mapping, constraints, transaction rollback, projections |
| `search:domain` | StayPeriod / StayAvailability / value invariants as pure unit tests |
| `search:application` | Supplier orchestration, mapping join, complete/partial/unavailable outcomes |
| `integration:supplier-*` | HTTP request shape, DTO translation, price semantics, batching, timeout/failure mapping |
| `search:adapter:web` | request validation and SearchOutcome → HTTP contract |
| `app` | smallest set of cross-module end-to-end/core-flow tests |
| `mock-supplier` | required normal/error/no-response modes |

Do not duplicate the same assertion at every layer. Test a rule at the lowest layer that owns it, then use
higher-level tests only to prove collaboration/wiring.

Do not create `:test-support` until repeated test infrastructure justifies it.

---

# 23. Architecture Enforcement

Gradle dependencies are the primary boundary.

Add package/bytecode architecture tests where Gradle alone is insufficient.

Recommended rules:

```text
domain → no Spring/JPA/WebClient
search → no Catalog internal packages
integration → no Catalog/Search application or adapter packages
shared infrastructure → no Catalog/Search dependencies
web adapter → no persistence adapter
app → composition only
```

ArchUnit or equivalent may enforce these rules.

---

# 24. Gradle Dependency Visibility

Use:

```text
api(...)
```

only when dependency types appear in a module's public signatures.

Otherwise use:

```text
implementation(...)
```

Examples:

```text
search:port public signatures may expose search:domain types
catalog:port public signatures may expose catalog:api/domain types
Supplier adapter implementation dependencies remain implementation
```

Avoid unnecessary transitive classpaths.

---

# 25. Package Convention

Illustrative:

```text
catalog
├── api
├── domain
├── port.in
├── port.out.persistence
├── port.out.supplier
├── application
└── adapter.persistence

search
├── domain
├── port.in
├── port.out.supplier
├── application
└── adapter.web

integration
├── suppliera
└── supplierb

shared
└── infrastructure
    └── id

app
└── composition
```

Gradle modules are the primary boundary; packages refine it.

---

# 26. Guardrails

```text
Search → Catalog only through :catalog:api

Catalog/Search never depend on Supplier implementation modules

Supplier adapters depend inward on consumer-owned Ports

Domain/API/Port modules stay framework-free

Application never handles Supplier DTO, JPA Entity, or Web DTO

Web adapter never queries persistence directly

Catalog sync network I/O never runs inside DB transaction

Search never stores live Supplier price/inventory

Supplier calls never use unbounded concurrency

:app composes behavior; it does not define business behavior

Mock Supplier never becomes a production dependency

Snowflake mechanism may be shared; Catalog identity semantics may not

PropertyIdGenerator / RoomTypeIdGenerator remain Catalog-owned Ports

Snowflake-to-Catalog ID binding occurs only at Composition Root
```

Do not create business/common dumping grounds such as:

```text
:shared:kernel
:common-domain
:integration-core
:integration-routing
```

without a concrete semantic need.

`:shared:infrastructure` is the explicit exception for narrowly scoped, domain-neutral technical mechanisms.
It must not absorb business concepts simply to make dependencies convenient.

---

# 27. Decision Summary

| Concern | Decision |
|---|---|
| Deployment | Modular Monolith |
| Business BCs | Catalog, Search |
| Supplier boundary | ACL/outbound adapter |
| Catalog published contract | `:catalog:api` |
| Search → Catalog | `:catalog:api` only |
| Supplier identity | `:catalog:api` string-backed `@JvmInline value class SupplierId` |
| Supported Supplier set | Composition Root/configuration; not a closed enum |
| Supplier routing | `Map<SupplierId, Port>` |
| Routing module | None |
| Supplier modules | One per Supplier |
| Catalog/Availability adapters | Separate classes |
| HTTP server | Spring MVC |
| Supplier HTTP | WebClient |
| Async | Kotlin structured coroutines |
| Persistence | PostgreSQL, Catalog only |
| Search data | Live, not persisted |
| Catalog read for Search | Projection via Published Contract |
| Catalog sync transaction | One validated Supplier snapshot |
| Network call inside DB tx | Forbidden |
| Supplier-level execution | Parallel |
| Batch execution | Bounded parallel |
| Shared business module | None by default |
| Shared technical infrastructure | `:shared:infrastructure` |
| Internal ID strategy | Snowflake-backed positive `Long` |
| ID generation Ports | Catalog-owned |
| Snowflake algorithm | Shared technical infrastructure |
| Snowflake → Catalog ID binding | `:app` Composition Root |
| Mock | Separate executable |

---

# 28. Evolution Rules

### Supplier C

```text
add module
implement existing Ports
register in Composition Root
```

Do not change Catalog/Search merely because C's protocol differs.

### New persistent Search state

Add Search persistence only when Search gains a real persistent lifecycle or query need.

### Service extraction

Split a module into a separate process only when deployment/scaling/ownership evidence justifies it.

### Shared abstraction

Business abstractions are shared only after a stable common semantic responsibility is demonstrated.

Domain-neutral technical mechanisms may live in `:shared:infrastructure`, but this module must not become a
shortcut for moving business ownership out of Catalog/Search.

Do not use shared abstractions to bypass dependency rules.
