# API.md

> Canonical public HTTP API contract.
>
> - Domain semantics: `DOMAIN.md`
> - Application outcomes: `USE_CASES.md`
> - Supplier protocols/failures: `INTEGRATION.md`
> - Runtime boundaries: `ARCHITECTURE.md`
> - ID strategy: `PERSISTENCE.md`
>
> Defines the public Search HTTP contract only.
> Supplier DTOs, persistence models, external product codes, and internal failure reasons never cross this boundary.

---

## 1. Public API Scope

Current business endpoint:

```http
GET /api/v1/stays/search
```

Example:

```http
GET /api/v1/stays/search?checkIn=2026-09-01&checkOut=2026-09-04&adults=2&children=0
```

Search criteria are limited to:

```text
stay dates
guest composition
```

Do not add without a new requirement:

```text
region / keyword
sorting / pagination
room count
child ages
```

Catalog synchronization is internal and has no public HTTP endpoint.
Mock Supplier endpoints are outside this contract.

---

# 2. Request Contract

All query parameters are required.

| Parameter | Type | Validation |
|---|---|---|
| `checkIn` | ISO local date | `YYYY-MM-DD` |
| `checkOut` | ISO local date | `YYYY-MM-DD`, `checkIn < checkOut` |
| `adults` | integer | `>= 1` |
| `children` | integer | `>= 0` |

Do not introduce speculative restrictions such as:

```text
checkIn >= today
maximum stay length
maximum guest count
```

## Validation ownership

Web adapter:

```text
required parameter presence
date/integer parsing
basic numeric validation
```

Search Domain:

```text
SearchCondition / StayPeriod invariants
```

All client-originated invalid Search conditions map to the same public `400` contract, including Spring MVC
binding/conversion errors.

---

# 3. Successful Search Response

```text
SearchResponse
├── searchStatus
├── offers[]
└── degradedSuppliers[]
```

Example:

```json
{
  "searchStatus": "COMPLETE",
  "offers": [
    {
      "propertyId": "783429837492837401",
      "propertyName": "Riverside Hotel Seoul",
      "roomTypeId": "783429837492837402",
      "roomTypeName": "Deluxe Twin",
      "maxOccupancy": 2,
      "availableRooms": 1,
      "supplier": "A",
      "price": {
        "amount": 429000,
        "currency": "KRW"
      },
      "breakfastIncluded": false
    }
  ],
  "degradedSuppliers": []
}
```

The response uses a flat Offer list.

```text
StayOffer → OfferResponse
```

Do not introduce a synthetic Property → RoomType → Offer hierarchy unless a future consumer requires it.

---

# 4. `searchStatus`

Allowed values:

```text
COMPLETE
PARTIAL
```

Mapping:

```text
SearchOutcome.Result with no failures
→ COMPLETE

SearchOutcome.Result with one or more failures
→ PARTIAL
```

Offer count does not determine status.

Therefore all are valid:

```text
COMPLETE + offers
COMPLETE + empty offers
PARTIAL  + offers
PARTIAL  + empty offers
```

Total unavailability is not a success status; it maps to HTTP `503`.

---

# 5. Offer Contract

```text
OfferResponse
├── propertyId        string
├── propertyName      string
├── roomTypeId        string
├── roomTypeName      string
├── maxOccupancy      integer
├── availableRooms    integer
├── supplier          string

`SupplierId` is serialized as a plain string. The public contract does not expose the internal value-object wrapper or define Supplier A/B as a closed enum.
├── price
│   ├── amount        integer
│   └── currency      string
└── breakfastIncluded boolean
```

Rules:

```text
maxOccupancy   > 0
availableRooms >= 0
```

All fields are present for every Offer.

---

# 6. Internal ID Serialization

Internal representation:

```text
PropertyId → Snowflake Long
RoomTypeId → Snowflake Long
```

Public JSON representation:

```text
decimal string
```

Example:

```json
{
  "propertyId": "783429837492837401",
  "roomTypeId": "783429837492837402"
}
```

Reason:

Snowflake 64-bit values can exceed JavaScript's safe integer range.

```text
Domain / DB → Long / BIGINT
HTTP JSON   → string
```

Do not expose these IDs as JSON numbers.

---

# 7. Catalog Metadata

Offer metadata comes from the Catalog Published Read Contract:

```text
propertyName
roomTypeName
maxOccupancy
```

Availability responses are not a second metadata authority.

---

# 8. Supplier Representation

Expose the source Supplier:

```json
{
  "supplier": "A"
}
```

Do not expose:

```text
supplierPropertyCode
supplierRoomTypeCode
```

External Supplier codes remain Integration concerns.

Public consumers use:

```text
propertyId
roomTypeId
```

---

# 9. Price Contract

```json
{
  "price": {
    "amount": 429000,
    "currency": "KRW"
  }
}
```

Meaning:

> Customer-facing total price for the entire requested StayPeriod.

Serialization:

```text
amount   → integer in the currency's minor unit
currency → uppercase ISO 4217 code
```

Do not expose or fabricate:

```text
nightlyRate
taxAmount
taxIncluded
dailyPrices
common tax breakdown
```

No implicit FX conversion is performed.

---

# 10. Availability Contract

```json
{
  "availableRooms": 0
}
```

`availableRooms` means the number of rooms bookable for the **entire** requested StayPeriod.

`0` is a valid commercial result and remains in `offers`.

Do not add a duplicate:

```text
available boolean
```

`availableRooms` is the single source of truth.

---

# 11. Offer Conditions

Current condition:

```json
{
  "breakfastIncluded": false
}
```

This is live Offer data, not Catalog RoomType metadata.

---

# 12. Partial / Degraded Result

When valid results remain but one or more Supplier contributions fail:

```http
200 OK
```

```json
{
  "searchStatus": "PARTIAL",
  "offers": [
    {
      "propertyId": "783429837492837401",
      "propertyName": "Riverside Hotel Seoul",
      "roomTypeId": "783429837492837402",
      "roomTypeName": "Deluxe Twin",
      "maxOccupancy": 2,
      "availableRooms": 1,
      "supplier": "A",
      "price": {
        "amount": 429000,
        "currency": "KRW"
      },
      "breakfastIncluded": false
    }
  ],
  "degradedSuppliers": ["B"]
}
```

`degradedSuppliers` means:

> At least one contribution associated with this Supplier could not be used.

It does **not** necessarily mean the Supplier produced zero valid Offers.

This distinction supports batch-level partial success:

```text
Supplier A
├── batch success
├── batch failure
└── batch success

→ A may appear in offers
→ "A" also appears once in degradedSuppliers
```

Rules:

```text
each Supplier appears at most once
internal failures are deduplicated by SupplierId
failure reason/count/batch identity are not exposed
ordering is not guaranteed
```

Do not use `206 Partial Content` or `207 Multi-Status`.

---

## 12.1 Public Projection & Intentional Information Reduction

The public Search API exposes the stable information required by the Search contract, not every field received
from Suppliers.

| Internal / upstream information | Public result |
|---|---|
| Supplier Property/Room codes | internal IDs + Supplier source |
| Supplier A nightly/tax breakdown | whole-stay `price` only |
| `DailyInventory[]` | derived `availableRooms` |
| raw Supplier failure category/detail | `degradedSuppliers` or `SEARCH_UNAVAILABLE` |
| Supplier availability names/occupancy | Catalog-authoritative names/maxOccupancy |

This reduction is intentional. The API must not imply data precision or structure that every supported Supplier
cannot provide.

---

# 13. Internal Failure Information

Internal failure taxonomy may include:

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

None are public Search-response fields.

Do not expose:

```text
Supplier resultCode
raw upstream HTTP status/body
WebClient exception
exception message/stack trace
batch number/count
normalization details
```

These remain in internal outcomes, metrics, and diagnostics.

---

# 14. Empty Search

Legitimate empty result:

```http
200 OK
```

```json
{
  "searchStatus": "COMPLETE",
  "offers": [],
  "degradedSuppliers": []
}
```

Examples:

```text
usable Catalog baseline established + no searchable Catalog target
all relevant Supplier executions succeed with no Offers
```

Empty result is not an error **only when the Catalog baseline precondition is satisfied**.

```text
known empty Catalog
→ 200 COMPLETE + empty offers

Catalog baseline unavailable
→ not an empty Search
→ 503 SEARCH_UNAVAILABLE
```

---

# 15. Partial Search with Zero Offers

This is also valid:

```text
Supplier A → legitimate successful empty result
Supplier B → failure
```

Response:

```http
200 OK
```

```json
{
  "searchStatus": "PARTIAL",
  "offers": [],
  "degradedSuppliers": ["B"]
}
```

Search success is independent of Offer count.

---

# 16. Search Unavailable

The public Search API is unavailable in either case:

```text
A. Search bootstrap gate is closed
   because one or more configured Suppliers have no usable Catalog baseline

B. Catalog baseline is established,
   normal Search executes,
   and every relevant Supplier execution fails
```

Case B is represented internally as:

```text
SearchOutcome.Unavailable
```

Case A is rejected before normal `SearchStaysUseCase` execution and does **not** require a new Search domain state.

Both map to:

```text
503 Service Unavailable
```

Canonical response:

```json
{
  "code": "SEARCH_UNAVAILABLE",
  "message": "Availability information is temporarily unavailable.",
  "suppliers": ["A", "B"]
}
```

Rules:

```text
suppliers contains distinct affected Supplier IDs

bootstrap-gate failure
→ include configured Supplier IDs whose Catalog baseline is unavailable

normal Search unavailable
→ include affected queried Supplier IDs

ordering is not guaranteed
internal failure reasons are not exposed
```

Do not add `Retry-After` without an actual retry-time policy.

---

# 17. Upstream Status Isolation

Upstream status is not passed through directly.

```text
Supplier 401 ≠ public 401
Supplier 429 ≠ public 429
Supplier 500 ≠ automatically public 500
Supplier 503 ≠ automatically public 503
```

Public policy:

```text
Catalog baseline gate CLOSED
→ 503 SEARCH_UNAVAILABLE

Catalog baseline gate OPEN
+ at least one relevant Supplier execution succeeds
→ 200 COMPLETE/PARTIAL

Catalog baseline gate OPEN
+ all relevant Supplier executions fail
→ 503 SEARCH_UNAVAILABLE
```

This keeps Supplier protocol semantics behind the Integration boundary.

---

# 18. Invalid Request

All invalid client Search requests map to:

```http
400 Bad Request
```

Canonical response:

```json
{
  "code": "INVALID_SEARCH_CONDITION",
  "message": "Invalid search condition."
}
```

Examples:

```text
missing required parameter
invalid date/integer format
checkIn >= checkOut
adults < 1
children < 0
```

Spring MVC argument binding/conversion failures must also be normalized to this response contract.

---

# 19. Unexpected Failure

Unexpected server failure maps to:

```http
500 Internal Server Error
```

Canonical response:

```json
{
  "code": "INTERNAL_ERROR",
  "message": "An unexpected error occurred."
}
```

Never expose raw exception messages or stack traces.

---

# 20. Error Contract Stability

Stable machine-readable error codes:

```text
INVALID_SEARCH_CONDITION
SEARCH_UNAVAILABLE
INTERNAL_ERROR
```

`code` and HTTP status are the programmatic contract.

`message` is human-readable and clients must not branch on its exact text.

Current response types:

```text
ApiErrorResponse
├── code
└── message

SearchUnavailableResponse
├── code
├── message
└── suppliers[]
```

Do not introduce a generic nested error framework for the current single endpoint.

---

# 21. HTTP Status Matrix

| Situation | HTTP | Contract |
|---|---:|---|
| Complete result | `200` | `SearchResponse / COMPLETE` |
| Established Catalog baseline + empty result | `200` | `SearchResponse / COMPLETE` |
| Partial/degraded result | `200` | `SearchResponse / PARTIAL` |
| Invalid SearchCondition | `400` | `INVALID_SEARCH_CONDITION` |
| Catalog bootstrap gate closed | `503` | `SEARCH_UNAVAILABLE` |
| Every relevant Supplier fails after normal Search starts | `503` | `SEARCH_UNAVAILABLE` |
| Unexpected server failure | `500` | `INTERNAL_ERROR` |

Do not use for current Search semantics:

```text
206
207
404 for empty result
401 for Supplier authentication failure
429 for Supplier rate limiting
```

---

# 22. Ordering

No ranking/sorting requirement exists.

Therefore ordering is not part of the API contract for:

```text
offers
degradedSuppliers
503 suppliers
```

Tests must compare semantic content without relying on concurrent completion order.

---

# 23. Caching

Successful and unavailable Search responses represent live Supplier data/state.

Set:

```http
Cache-Control: no-store
```

for:

```text
200 Search responses
503 Search unavailable responses
```

Do not accidentally turn intermediary HTTP caching into the price/inventory caching policy.

---

# 24. Content Type

JSON responses:

```http
Content-Type: application/json
```

The GET request has no body.

---

# 25. DTO Boundary

Keep transport models separate.

```text
Domain/Application
├── SearchCondition
├── StayOffer
└── SearchOutcome

Web
├── SearchResponse
├── OfferResponse
├── PriceResponse
├── ApiErrorResponse
└── SearchUnavailableResponse
```

Query parameters may be bound directly or through a web request model; that is an adapter implementation detail.

Do not serialize Domain/Application objects directly.

---

# 26. Serialization Rules

```text
LocalDate      → YYYY-MM-DD
Snowflake ID   → decimal JSON string
Money amount   → integer JSON number
Currency       → uppercase ISO 4217 string
Supplier       → stable public string identifier
Boolean        → JSON boolean
```

Never serialize:

```text
enum ordinals
Kotlin class names
JPA entities
Supplier protocol DTOs
external product codes
```

---

# 27. Controller / Web Adapter Responsibility

Web adapter:

```text
parse/bind request
normalize binding/validation failures
create application input
call SearchStaysUseCase
map SearchOutcome to HTTP contract
convert Snowflake IDs to strings
set Cache-Control where specified
```

Web adapter must not:

```text
call Supplier adapters directly
query Catalog persistence
calculate availability
normalize Supplier prices
inspect raw Supplier error/resultCode
decide Catalog mappings
```

---

# 28. OpenAPI

Expose the endpoint through SpringDoc/OpenAPI.

Document:

```text
GET /api/v1/stays/search
all required query parameters
date/numeric validation
Snowflake IDs as strings
Offer field semantics
COMPLETE / PARTIAL
degradedSuppliers semantics
200 / 400 / 503 / 500 contracts
```

OpenAPI annotations/configuration stay in the web adapter.

`API.md` is the canonical semantic contract; generated OpenAPI must match it.

---

# 29. Web Contract Verification

Web tests verify:

```text
required parameters
date/integer parsing
numeric/domain validation → canonical 400
Snowflake ID string serialization

COMPLETE mapping
PARTIAL mapping
degraded Supplier deduplication
empty COMPLETE
empty PARTIAL

Unavailable → canonical 503
unexpected error → canonical 500

response field shapes
Cache-Control
```

Do not retest here:

```text
StayAvailability calculation
Supplier A/B protocol semantics
Supplier price normalization
Catalog reconciliation
```

Those belong to their owning layers.

Detailed requirement-to-test traceability belongs in `VERIFICATION.md`.

---

# 30. Guardrails

```text
StayOffer ≠ HTTP DTO

Supplier external code ≠ public internal ID

Snowflake Long ≠ JSON number

Supplier upstream status ≠ public HTTP status

partial/degraded result → 200

empty result → 200

all relevant Suppliers failed → 503

availableRooms=0 ≠ error

failure reason/count/batch details stay internal

Offer/Supplier ordering is not guaranteed

live Search response uses no-store
```

---

# 31. Decision Summary

| Concern | Decision |
|---|---|
| Public endpoint | `GET /api/v1/stays/search` |
| Query parameters | `checkIn`, `checkOut`, `adults`, `children` |
| Required | All |
| Date format | `YYYY-MM-DD` |
| Adults | `>= 1` |
| Children | `>= 0` |
| Response | Flat `offers[]` |
| Snowflake IDs | JSON string |
| Price | Whole-stay `{amount, currency}` |
| Zero inventory | Retained with `availableRooms=0` |
| Availability boolean | None |
| Breakfast | Exposed |
| External product codes | Not exposed |
| Offer Supplier | Exposed |
| Complete status | `COMPLETE` |
| Partial status | `PARTIAL` |
| Degradation detail | Distinct Supplier IDs only |
| Complete/empty/partial HTTP | `200` |
| Invalid Search | `400` |
| All relevant Suppliers fail | `503` |
| Unexpected failure | `500` |
| Upstream status passthrough | No |
| Ordering | Not guaranteed |
| Cache | `no-store` on `200`/`503` |
| OpenAPI | Yes |
| Direct Domain serialization | No |
