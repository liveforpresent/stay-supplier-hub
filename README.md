# stay-supplier-hub

Multi-supplier accommodation catalog and live availability integration service.

> Detailed design documentation lives under `docs/`.

## Scope

### Current core

- **Catalog** — stable Supplier-backed Property/RoomType identity, mapping, metadata, and synchronization.
- **Search** — live whole-stay price, availability, offer conditions, and integrated result semantics.
- **Supplier Integration** — ACL/outbound adapter boundary for Supplier-specific protocols and failures.

### Designed for evolution

The broader problem space is considered without pre-creating speculative Bounded Contexts:

- cross-Supplier Property canonicalization,
- Reservation / Booking lifecycle,
- common-currency conversion,
- retry / circuit breaker,
- price & inventory cache,
- normalization quarantine.

These areas are revisited only when a concrete requirement introduces the identity, lifecycle, consistency, or
operational policy needed to define the boundary correctly.

### Explicitly out of scope

- end-user authentication / authorization,
- payment integration,
- administrator features,
- frontend,
- real commercial Supplier APIs,
- region / keyword search,
- sorting / pagination.

See `docs/DOMAIN.md#8-domain-scope--evolution` for the scope rationale and boundary triggers.

## Batching & Concurrency

Supplier availability APIs accept at most 50 Property codes per request. Larger target sets are split into
50-Property batches and executed with bounded concurrency.

Initial default:

```text
max batch concurrency per Supplier / application instance = 5
```

`5` is a conservative starting point, not a measured optimum or Supplier SLA. It avoids serial execution while
preventing one application instance from creating unbounded fan-out. With 50 Properties per request, at most five
active batch calls represent up to 250 Property targets in flight for one Supplier at a time.

The limiter is shared by all concurrent customer Searches using the same Supplier adapter instance. It is not
recreated per Search request.

The value is independently configurable per Supplier and should be tuned from latency, timeout, rate-limit, and
failure metrics.

Per-request timeout and whole-Supplier execution latency are intentionally distinct:

```text
2s response timeout
≠
2s total Supplier execution deadline
```

Many queued batches can require multiple timeout waves. The current implementation does not claim a fixed
end-to-end Supplier latency bound; a Supplier-level execution deadline/budget is a documented production
evolution if scale requires one.

## Unified Model — Information Policy

Supplier payloads are not copied into a large shared superset. The service preserves information with a stable
shared meaning, derives common values where Supplier representations differ, uses some fields only for validation,
and deliberately removes protocol-specific detail.

| Information | Policy | Reason |
|---|---|---|
| Supplier Property/Room codes | Preserve internally | Stable mapping and live-response join |
| Internal Property/Room IDs | Preserve | Stable internal/public identity |
| Property/Room names, maxOccupancy | Preserve from Catalog | Single metadata authority |
| Supplier A nightly price/tax | Derive then discard | Used to calculate whole-stay gross total |
| Supplier B totalPrice | Preserve | Already whole-stay gross total |
| Supplier B taxIncluded | Validate then discard | Confirms gross-price semantics |
| Currency | Preserve | No implicit FX conversion |
| Daily inventory | Preserve transiently, then derive | Required for complete-stay availability |
| availableRooms | Derive and preserve | Common whole-stay availability |
| breakfastIncluded | Preserve | Search-dependent commercial condition |
| Availability-response names/occupancy | Discard as authority | Catalog remains metadata authority |
| Raw Supplier errors | Normalize, then hide publicly | Prevent protocol leakage |
| External codes in public Search API | Discard | Public contract uses internal IDs |
| Nightly/tax breakdown in public API | Discard | Not truthfully available from every Supplier |

The common price meaning is:

```text
whole-stay customer-facing total + currency
```

Missing Supplier information is never fabricated.

Daily inventory remains an internal validation/derivation input. The public API exposes the resulting number of
rooms available for the complete requested stay.

See `docs/INTEGRATION.md` and `docs/DOMAIN.md` for detailed ownership and normalization rules.

## Engineering Process

Process records are kept separate from normative design documents:

- [`JOURNAL.md`](JOURNAL.md) — meaningful engineering decisions, problems, alternatives, and outcomes.
- [`AI_USAGE.md`](AI_USAGE.md) — significant AI assistance, what was accepted/modified/rejected/deferred, and how the result was verified.

Canonical design truth remains under `docs/`; Git history records exact repository changes.
