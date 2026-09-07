# JOURNAL.md

## Day 1 — 요구사항 해석과 도메인 경계 정의

**Type:** Decision / Significant Work

### Context

구현을 시작하기 전에 요구사항을 기능 목록으로 바로 옮기기보다, 구현 과정에서 해석이 달라질 수 있는 지점을 먼저 정리했다.

특히 다음 항목은 코드 작성 전에 기준을 명확히 하지 않으면 Supplier별 구현이나 Search 흐름에서 서로 다른 해석이 생길 수 있다고 판단했다.

- Supplier 상품과 내부 `Property` / `RoomType` 식별자를 어떻게 연결할지
- Catalog의 정적 정보와 Search 시점의 가격·재고를 어디에서 소유할지
- 여러 날짜의 재고를 전체 숙박 기간의 가용 수량으로 어떻게 계산할지
- 한 Supplier 또는 일부 batch가 실패했을 때 전체 검색 결과를 어떻게 다룰지
- Supplier별로 다른 가격 표현을 어떤 공통 의미로 정규화할지
- Catalog 동기화에서 사라진 상품을 삭제할지 비활성화할지

이를 바탕으로 요구사항, 용어, 도메인 규칙, 애플리케이션 흐름을 각각 분리해 정리했다.

### Considered

초기에는 숙박 상품, Supplier 연동, 실시간 검색을 하나의 큰 도메인으로 다루는 방식과 Supplier Integration까지 별도 Bounded Context로 분리하는 방식도 검토했다.

하지만 Catalog 데이터와 Search 데이터는 성격이 달랐다.

- Catalog는 내부 식별자, Supplier mapping, 상품명, 최대 수용 인원, ACTIVE/INACTIVE 상태처럼 비교적 안정적인 정보를 다룬다.
- Search는 요청 시점마다 달라지는 가격, 재고, 조식 포함 여부와 같은 실시간 정보를 다룬다.
- Supplier Integration은 독립적인 비즈니스 규칙보다는 외부 프로토콜을 내부에서 요구하는 형태로 번역하는 책임이 중심이었다.

`RoomType`을 별도 Aggregate로 분리하는 방안도 검토했지만, 현재 요구사항에서는 `RoomType`이 `Property`와 독립적으로 변경되거나 별도의 command/lifecycle/concurrency boundary를 가질 필요가 없었다.

### Decision

도메인 경계를 다음과 같이 정했다.

- **Catalog BC**
  - 내부 `PropertyId` / `RoomTypeId`
  - Supplier ↔ 내부 식별자 mapping
  - Property / RoomType metadata
  - ACTIVE / INACTIVE lifecycle
  - Supplier Catalog synchronization

- **Search BC**
  - `SearchCondition`
  - `StayPeriod`
  - `GuestComposition`
  - 전체 숙박 기간의 가격과 가용 수량
  - `StayOffer`
  - 여러 Supplier 결과를 합친 Search outcome

- **Supplier Integration**
  - 별도 Bounded Context가 아니라 ACL / outbound adapter boundary
  - Supplier별 DTO, HTTP protocol, 인증, 오류 형식 차이를 내부 계약으로 변환

Aggregate는 `Property`를 Aggregate Root, `RoomType`을 그 내부 Entity로 두었다.

Catalog 동기화는 정상적으로 수집·검증된 전체 snapshot을 기준으로 수행하고, snapshot에서 사라진 상품은 삭제하지 않고 `INACTIVE`로 전환하도록 했다. 이후 동일한 외부 식별자가 다시 등장하면 기존 내부 ID를 유지한 채 재활성화한다.

### Why

가장 중요한 기준은 서로 다른 생명주기와 변경 원인을 가진 데이터를 하나의 모델에 섞지 않는 것이었다.

Catalog와 Search를 분리하면 Supplier의 실시간 재고나 가격 변화가 내부 상품 식별자와 lifecycle에 영향을 주지 않는다. Supplier-specific protocol 역시 내부 도메인 모델로 직접 유입되지 않고 ACL에서 차단할 수 있다.

또한 `Property`와 `RoomType`을 현재 요구사항보다 더 잘게 Aggregate로 분리하지 않음으로써, 실제 독립적인 변경 경계가 없는 상태에서 불필요한 복잡성을 만들지 않도록 했다.

### Outcome / Impact

구현 전 다음 기준을 고정했다.

- Supplier의 외부 식별자와 내부 식별자를 분리한다.
- 같은 Supplier 상품은 재동기화해도 동일한 내부 ID를 유지한다.
- Search는 Catalog 내부 저장 구조나 Aggregate에 직접 접근하지 않는다.
- 실시간 가격·재고는 Catalog에 저장하지 않는다.
- Supplier-specific 모델은 Integration 경계 밖으로 노출하지 않는다.
- 현재 요구사항에서 필요한 최소 Aggregate 경계를 유지한다.

이후 구현과 테스트에서 사용되는 용어와 책임 경계를 같은 기준으로 맞출 수 있게 되었다.

---

## Day 2 — 구현 아키텍처와 Supplier 통합 전략 구체화

**Type:** Decision / Significant Work

### Context

Day 1에서 정한 도메인 경계를 실제 Kotlin/Spring 코드 구조로 어떻게 유지할지 구체화했다.

단순히 package만 나누는 것으로는 Search가 Catalog persistence에 직접 접근하거나 Supplier-specific DTO가 application layer까지 침투하는 것을 막기 어렵다고 판단했다.

또한 Supplier A/B는 가격, 실패 표현, batch 제한 방식이 달라서 공통화 범위를 잘못 잡으면 Supplier-specific semantics를 내부 모델에 끌고 들어오거나, 반대로 실제로 존재하지 않는 정보를 만들어낼 위험이 있었다.

### Considered

배포 단위까지 Catalog와 Search를 분리하는 Microservice 구조도 가능했지만, 현재 범위에서는 독립 배포나 별도 확장 요구보다 명확한 모델 경계가 더 중요했다.

따라서 운영 복잡성을 늘리지 않으면서도 코드 수준에서 의존성을 통제할 수 있는 Modular Monolith를 선택했다.

Supplier Integration에서는 다음 방식을 비교했다.

- Integration이 하나의 공통 Supplier service/model을 정의하고 Catalog/Search가 이를 사용
- Supplier adapter가 Catalog/Search가 정의한 outbound Port를 각각 구현
- Supplier별 차이를 application layer에서 직접 분기

가격의 경우에도 Supplier A의 일별 가격 구조를 기준으로 모든 Supplier를 맞추는 방식과, Supplier별 가격 모델을 그대로 application까지 노출하는 방식을 검토했다.

### Decision

런타임은 하나의 Spring Boot application을 유지하되 Gradle multi-module 기반의 **Modular Monolith**로 구성하기로 했다.

핵심 모듈 구조는 다음과 같이 정리했다.

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

Search는 Catalog repository나 JPA entity를 직접 참조하지 않고 `:catalog:api`의 Published Read Contract만 사용한다.

Supplier 연동은 **consumer-owned Port** 방식으로 구성했다.

- Catalog는 자신에게 필요한 `SupplierCatalogPort`를 정의한다.
- Search는 자신에게 필요한 `SupplierAvailabilityPort`를 정의한다.
- Supplier A/B adapter가 각각 해당 Port를 구현한다.
- application layer는 `SupplierId`를 기준으로 Port 구현을 선택하며 concrete Supplier adapter에 의존하지 않는다.

Supplier별 가격 차이는 공통 내부 의미를 **전체 숙박 기간의 가격(Whole-Stay Price)** 으로 잡아 정규화하기로 했다.

- Supplier A: 필요한 모든 날짜의 `nightlyRate + taxAmount`를 합산
- Supplier B: Supplier가 제공한 전체 숙박 가격을 그대로 사용
- Supplier가 제공하지 않는 일별 가격이나 세금 breakdown은 임의로 생성하지 않음

검색 실패는 Supplier 단위 또는 batch 단위로 격리한다.

- 하나 이상의 Supplier 실행이 정상적으로 완료되면 사용 가능한 결과를 반환
- 일부 실패가 있으면 partial result로 표현
- 모든 relevant Supplier 실행이 실패했을 때만 Search unavailable로 처리

Catalog 동기화에서는 외부 HTTP 요청과 DB transaction을 분리하기로 했다.

```text
Supplier fetch
→ response normalization / validation
→ DB transaction 시작
→ Catalog reconciliation
→ commit
```

Supplier 응답을 기다리는 동안 DB transaction을 열어 두지 않는다.

### Why

Modular Monolith는 현재 서비스 규모에서 별도 배포 복잡성을 만들지 않으면서도 Catalog/Search 간 의존 방향을 코드 수준에서 명확히 표현할 수 있다.

consumer-owned Port를 사용하면 내부 애플리케이션이 Supplier가 제공하는 API 구조를 기준으로 설계되지 않는다. 새로운 Supplier가 추가되더라도 기존 Catalog/Search의 의미를 바꾸기보다 새로운 adapter가 기존 계약에 맞춰지는 구조를 유지할 수 있다.

가격 역시 Supplier별 표현을 억지로 동일한 구조로 만드는 대신, 양쪽이 실제로 보장할 수 있는 가장 좁은 공통 의미를 선택했다. 이를 통해 Supplier가 제공하지 않은 정보를 내부에서 추정하거나 조작하지 않도록 했다.

Partial failure 정책은 한 외부 Supplier의 장애가 다른 Supplier의 정상 결과까지 무효화하지 않도록 하기 위한 결정이다.

### Verification Strategy

설계 문서만으로 구현 완료 여부를 판단하지 않기 위해 요구사항과 검증 시나리오를 연결하는 기준을 세웠다.

```text
Requirement
→ Verification scenario
→ Owning layer
→ Implementation
→ Evidence
```

검증은 가능한 한 해당 규칙을 소유하는 가장 낮은 계층에서 수행하도록 했다.

- Domain invariant → Domain unit test
- Application orchestration → Application test with fake Ports
- Supplier protocol → Supplier adapter integration test
- PostgreSQL / JPA → Persistence integration test
- HTTP contract → Web contract test
- Transaction / wiring → Cross-module test
- 핵심 사용자 흐름 → 최소 E2E

Codex가 같은 기준으로 작업할 수 있도록 canonical document, module ownership, 작업 순서, verification rule도 함께 정리했다.

### Project Initialization

설계 기준을 정리한 뒤 Spring Initializr로 기본 Kotlin/Spring Boot 프로젝트를 생성하고 개인 GitHub repository에 초기 상태를 push했다.

프로젝트 이름은 `stay-supplier-hub`, base package는 `com.staysupplierhub`로 정했다.

초기화 자체보다는, 이후 구현이 이미 정의한 도메인 경계와 검증 기준을 따라 진행될 수 있는 출발점을 만든 것에 의미를 두었다.

### Outcome / Impact

Day 2 종료 시점에는 다음 구현 기준이 정리됐다.

- 하나의 deployable 안에서 BC별 module boundary를 유지한다.
- Search는 Catalog persistence에 직접 접근하지 않는다.
- Supplier-specific protocol은 adapter 내부에서 끝난다.
- Catalog/Search가 필요한 contract를 직접 소유한다.
- Supplier별 가격 차이는 의미를 잃지 않는 범위에서만 정규화한다.
- 외부 HTTP latency가 Catalog DB transaction lifetime에 포함되지 않는다.
- 일부 Supplier 장애가 전체 검색 장애로 전파되지 않는다.
- 구현 전에 각 요구사항을 어떤 테스트/검증으로 닫을지 확인한다.

이 기준을 바탕으로 실제 multi-module 구성과 기능 구현을 시작할 수 있는 상태를 만들었다.

---

## Day 3 — Empty Catalog과 Unknown Catalog를 분리

**Type:** Problem / Decision

### Context

Startup Catalog synchronization과 Search empty-result 규칙을 함께 검토하면서 의미가 충돌하는 경우를 발견했다.

기존 Search 흐름은 searchable Catalog target이 없으면 Supplier를 호출하지 않고 정상적인 빈 결과를 반환한다.

```text
no searchable target
→ Result([], [])
→ 200 COMPLETE
```

이 규칙 자체는 정상적인 Catalog가 존재하지만 현재 검색 가능한 상품이 없는 경우에는 올바르다.

하지만 Fresh DB에서 Supplier Catalog synchronization이 실패해 mapping 자체를 확보하지 못한 경우에도
동일하게 target이 0개가 된다. 이 상태를 그대로 empty Search로 처리하면 실제로는 검색 대상을 알 수
없는 장애 상황을 "상품이 없음"으로 잘못 표현하게 된다.

### Considered

세 가지 방향을 검토했다.

1. Catalog synchronization 성공 여부와 관계없이 target이 없으면 항상 empty Search로 처리
2. Catalog를 확보한 Supplier만으로 부분 검색을 허용
3. 모든 configured Supplier가 usable Catalog baseline을 확보한 경우에만 정상 Search를 허용

1번은 `known empty`와 `unknown`을 구분하지 못해 의미적으로 부정확했다.

2번은 가용성은 높지만, Catalog 자체가 없는 Supplier를 Search가 정상적인 미등록 Supplier와 구분하기
위해 Catalog synchronization 상태를 Search 계약에 전달해야 했다. 현재 범위에서는 operational state가
Search 모델에 침투하는 비용이 크다고 판단했다.

### Decision

모든 configured Supplier에 대해 **usable Catalog baseline**이 있는지를 Search traffic의 전제조건으로 둔다.

```text
current startup sync 성공
→ baseline 있음

current startup sync 실패 + 이전 persisted Catalog state 있음
→ stale baseline 사용 가능

current startup sync 실패 + 이전 persisted Catalog state 없음
→ baseline 없음
```

모든 configured Supplier가 baseline을 확보했을 때만 Search traffic gate를 연다.

```text
all baselines available
→ Search OPEN

any baseline unavailable
→ Search CLOSED
→ 503 SEARCH_UNAVAILABLE
```

이 gate는 `SearchOutcome`에 새로운 상태를 추가하지 않고 application/operational layer에서
`SearchStaysUseCase` 실행 전에 적용한다.

### Why

Catalog mapping은 단순한 검색 캐시가 아니라 Supplier availability API에 전달할 Property code를 결정하는
검색의 전제 데이터다.

따라서 다음 두 상태는 구분되어야 한다.

```text
Catalog baseline established + searchable target = 0
→ "현재 검색할 상품이 없음"
→ 정상 empty result

Catalog baseline unavailable
→ "어떤 상품을 검색해야 하는지 알 수 없음"
→ Search unavailable
```

기존 Catalog가 있는 경우에는 Supplier 목록이 가격/재고보다 상대적으로 덜 자주 변한다는 특성을 이용해
현재 sync가 실패해도 이전 mapping을 baseline으로 사용할 수 있도록 했다. 가격과 재고는 기존 값을
사용하지 않고 여전히 Search 시점에 Supplier에서 조회한다.

### Outcome / Impact

- Fresh DB에서 Catalog bootstrap 실패가 `200 + empty offers`로 오인되지 않는다.
- 정상적으로 확립된 empty Catalog는 계속 `200 COMPLETE`로 표현할 수 있다.
- Supplier refresh 실패 시 기존 Catalog state가 있으면 stale mapping을 유지해 Search 가용성을 보존한다.
- bootstrap/readiness는 Search Domain이 아니라 application/operational concern으로 유지한다.
- `REQUIREMENTS.md`, `GLOSSARY.md`, `ARCHITECTURE.md`, `USE_CASES.md`, `PERSISTENCE.md`,
  `API.md`, `VERIFICATION.md`에 동일한 기준을 반영했다.

### Known Limitation

현재는 별도의 Supplier Catalog synchronization metadata를 저장하지 않는다.

따라서 이전에 정상적으로 동기화된 결과가 항상 완전히 빈 Catalog였고 DB에도 해당 Supplier의 Catalog
row가 하나도 없는 상태에서, 다음 프로세스 재시작 시 synchronization까지 실패한다면
"과거에 정상 empty baseline이 있었음"과 "한 번도 baseline을 확보하지 못했음"을 구분할 수 없다.

현재 데이터와 구현 범위를 고려해 이 edge case는 허용하고, 필요해질 경우
Supplier별 `lastSuccessfulCatalogSync`와 같은 metadata persistence를 추가하는 방향으로 확장한다.

---

## Day 3 — Supplier별 정보 차이를 공통 모델에 어떻게 반영할지 결정

**Type:** Decision

### Context

Supplier A와 B의 응답을 하나의 Search 모델로 정규화하면서, 단순히 공통 DTO를 만드는 것만으로는
어떤 정보를 유지하고 어떤 정보를 잃는지 설명하기 어려웠다.

Supplier A는 날짜별 요금과 세금을 제공하지만 Supplier B는 숙박 전체 총액만 제공한다.
A의 더 풍부한 구조를 공통 모델의 기준으로 삼으면 B에 없는 nightly/tax 정보를 `null`로 채우거나
임의로 만들어야 한다.

또한 availability 응답에는 Catalog에도 존재하는 이름과 `maxOccupancy`가 반복되고, 일별 inventory는
연박 가능 객실 수 계산에는 필요하지만 public Search 계약에서 그대로 노출할 필요는 없다.

### Considered

정보 처리를 단순한 "유지 / 삭제"가 아니라 다음 네 가지로 구분했다.

```text
PRESERVE
DERIVE / NORMALIZE
VALIDATE THEN DROP
DISCARD
```

공통 모델을 가장 정보가 많은 Supplier 기준으로 만드는 방식도 검토했다. 하지만 이는 다른 Supplier가
제공하지 않는 세부 데이터를 공통 계약에 포함시키고 null 또는 추정값을 유도할 수 있어 제외했다.

### Decision

공통 모델은 **모든 Supplier가 진실하게 제공할 수 있으면서 통합 Search에 필요한 가장 좁은 공통 의미**
를 기준으로 설계한다.

가격:

```text
Supplier A
Σ(nightlyRate + taxAmount)
        ↓
WholeStayPrice

Supplier B
totalPrice
        ↓
WholeStayPrice
```

`WholeStayPrice`는 요청한 전체 숙박 기간의 고객 지불 총액과 통화를 의미한다.

- A의 nightly price/tax는 전체 가격 계산 후 공통 Search 모델에 유지하지 않는다.
- B의 `taxIncluded`는 gross semantics를 검증한 뒤 유지하지 않는다.
- B가 제공하지 않는 nightly price/tax는 생성하지 않는다.

재고:

```text
DailyInventory[]
→ stay-date completeness validation
→ min(remainingRooms)
→ StayAvailability.availableRooms
```

일별 inventory는 계산과 검증을 위해 transient하게 유지하지만 public API에서는 whole-stay
`availableRooms`로 축약한다.

Metadata authority:

```text
Property/Room names, MaxOccupancy
→ Catalog

Price, Inventory, Breakfast
→ live Supplier response
```

Availability 응답에 반복되는 이름과 occupancy는 Catalog를 갱신하거나 Search metadata authority로
사용하지 않는다.

외부 code와 raw Supplier error는 내부 mapping/diagnostics에는 사용하지만 public Search contract에는
노출하지 않는다.

### Why

공통화의 목적은 Supplier payload를 구조적으로 동일하게 만드는 것이 아니라, Supplier 차이를 숨기면서도
의미를 왜곡하지 않는 안정적인 내부 계약을 만드는 것이다.

가장 풍부한 Supplier를 기준으로 공통 모델을 만들면 다른 Supplier의 정보 공백을 null이나 추정값으로
채우게 된다. 반대로 가장 좁은 공통 business semantics를 선택하면 Supplier가 실제로 제공한 정보만으로
동일한 Search 의미를 만들 수 있다.

정보 손실은 무조건 피해야 하는 것이 아니라, downstream에서 필요하지 않고 다른 Supplier가 동등하게
보장할 수 없는 세부 표현이라면 의도적으로 제거하는 편이 더 정확하다고 판단했다.

### Outcome / Impact

- 공통 가격 모델을 whole-stay total + currency로 고정했다.
- Supplier A nightly/tax breakdown은 derivation input으로만 사용한다.
- Supplier B nightly/tax breakdown은 생성하지 않는다.
- daily inventory는 whole-stay availability 계산 후 public contract에서 축약한다.
- Catalog와 live availability 사이 metadata authority를 명확히 했다.
- external codes/raw protocol failures가 public API로 누출되지 않는다.
- README에는 정보 보존/손실 정책의 요약을, `INTEGRATION.md`에는 상세 정책을 기록했다.

---

## Day 3 — 전체 도메인 설계와 과설계 사이의 경계 정하기

**Type:** Decision

### Context

요구사항의 "설계는 전체 도메인을 다룬다"는 문장을 현재 Catalog/Search 설계에 어떻게 반영할지 검토했다.

현재 `DOMAIN.md`에는 Catalog와 Search만 실제 Bounded Context로 정의되어 있고, Reservation, currency
conversion, canonicalization, cache, quarantine 같은 개념은 하나의 `Deliberately Unmodeled Concepts`
목록에 함께 들어 있었다.

이 상태는 현재 구현 범위는 명확하지만, 주변 문제 공간을 충분히 인지한 뒤 의도적으로 제외했다는
판단이 잘 드러나지 않았다.

반대로 "전체 도메인"을 숙박 플랫폼 전체로 해석해 Reservation, Payment, Pricing, Inventory 등의
Bounded Context와 Aggregate를 미리 설계하면 구체 요구사항이 없는 상태에서 미래 구조를 추측하게 된다.

### Considered

#### 미래 Bounded Context를 미리 설계

예상 가능한 Reservation, Pricing, Canonicalization 등을 독립 BC로 정의하는 방법을 검토했다.

하지만 현재는 각 영역의 실제 identity, lifecycle, consistency rule이 충분히 주어지지 않았다.
이 상태에서 tactical model까지 만들면 설계가 풍부해 보일 수는 있어도 근거 없는 구조가 된다.

#### 현재 Catalog/Search만 기술

현재 구현과 가장 정확히 일치하지만, cross-Supplier identity, Reservation, currency, cache 같은 인접
문제를 어떤 기준으로 제외했는지가 드러나지 않는다.

#### Current Core + Adjacent Capability + Evolution Trigger

현재 Bounded Context는 Catalog/Search로 유지하면서, 주변 영역은 현재 제외 이유와
"어떤 요구가 생기면 경계를 다시 검토할지"까지 문서화한다.

### Decision

전체 relevant domain을 다음 다섯 범주로 나눈다.

```text
1. Current Core
2. Adjacent Domain Capabilities
3. Infrastructure / Integration Evolution
4. Explicitly Unmodeled Product Concepts
5. Explicit Out of Scope
```

Current Core:

```text
Catalog BC
Search BC
Supplier Integration ACL
```

Adjacent Domain Capabilities:

```text
Cross-Supplier Canonicalization
Reservation / Booking
Currency Conversion
```

이들은 현재 Bounded Context로 확정하지 않는다. 대신 현재 제외 이유와 boundary trigger만 정의한다.

Infrastructure / Integration Evolution:

```text
Retry / Circuit Breaker
Price / Inventory Cache
Normalization Quarantine
```

이들은 기본적으로 기술/운영 capability이며, 별도 domain context로 만들지 않는다.

Explicitly Unmodeled Product Concepts:

```text
Individual Physical Room
Persistent RatePlan
Persistent Search History
```

현재 Supplier contract에 독립 identity/lifecycle 근거가 없으므로 모델링하지 않는다.

Explicit Out of Scope:

```text
Auth/AuthZ
Payment integration
Admin
Frontend
Real commercial Supplier APIs
Region/Keyword Search
Sorting/Pagination
```

Reservation은 adjacent optional evolution으로, Payment는 명시적 OOS로 구분한다.

### Why

전체 도메인을 다룬다는 것은 가능한 모든 미래 기능의 Aggregate를 미리 만드는 것이 아니라,
현재 문제 공간과 주변 경계를 이해하고 **왜 지금 이 모델까지만 필요한지 설명할 수 있는 것**이라고
판단했다.

특히 DDD 경계는 기능 이름만으로 정하는 것이 아니라 실제 identity, lifecycle, consistency boundary가
드러날 때 정해야 한다.

따라서:

```text
broader domain awareness
≠ speculative tactical modeling
```

을 원칙으로 삼는다.

### Outcome / Impact

- `DOMAIN.md`의 단일 unmodeled 목록을 `Domain Scope & Evolution` 구조로 재정리했다.
- Catalog/Search만 현재 Bounded Context로 유지한다.
- future capability를 현재 모듈/BC처럼 문서화하지 않는다.
- Reservation, canonicalization, currency는 boundary trigger까지 설명한다.
- Retry/cache/quarantine은 infrastructure/integration evolution으로 분리한다.
- Physical Room/RatePlan을 왜 모델링하지 않는지 구체적인 근거를 남긴다.
- 명시적 OOS는 adjacent capability와 분리한다.
- `ARCHITECTURE.md`에는 미래 모듈을 추가하지 않는다.

---

## Day 3 — Batch 동시성 5의 의미와 범위를 구체화

**Type:** Decision / Scalability

### Context

Supplier availability API는 한 요청에 최대 50개 Property code만 받을 수 있으므로, 검색 대상이 수천 개로
늘어나면 하나의 Supplier에도 수십 개 batch가 생긴다.

기존 설계는 batch를 최대 50개씩 나누고 `maxBatchConcurrency=5`로 제한했지만, 왜 5인지와 이 제한이
한 Search 요청에만 적용되는지 application instance 전체에 적용되는지가 충분히 명확하지 않았다.

또한 `response timeout=2s`와 bounded concurrency를 함께 사용하면 전체 Supplier 실행도 2초 안에
끝난다고 오해할 수 있음을 발견했다.

### Considered

#### Concurrency = 1

upstream 부하는 작지만 독립적인 외부 I/O를 순차 처리하므로 batch 수가 증가할수록 latency가
불필요하게 증가한다.

#### 높은 concurrency / unbounded fan-out

latency는 줄어들 수 있지만 Supplier SLA, rate-limit capacity, safe concurrency 수준이 주어지지 않았다.
한 고객 요청이 수십 개 HTTP call을 동시에 만들고 여러 고객 요청이 겹치면 upstream pressure가
급격히 커질 수 있다.

#### Conservative configurable default

순차 실행을 피하되 한 Supplier에 대한 동시 outbound call을 유한하게 제한하는 초기값을 두고,
운영 지표로 Supplier별 튜닝한다.

### Decision

초기값 `5`를 유지한다.

```text
max 50 Properties / batch
×
max 5 active batches
=
up to 250 Property targets in active outbound calls per Supplier
```

`5`는 다음이 아니다.

```text
business invariant
Supplier protocol constant
benchmark-derived optimum
production SLA
```

Supplier별 독립 configuration으로 두고, production에서는 다음 지표로 조정한다.

```text
Supplier request latency
timeout rate
RATE_LIMITED / 429-equivalent frequency
Supplier failure rate
```

Concurrency bound의 범위는 Search 요청 하나가 아니라 **Supplier adapter instance 전체**로 정의한다.

```text
Supplier A adapter
└── shared limiter(5)
    ├── Search #1 batches
    ├── Search #2 batches
    └── Search #3 batches
```

Search마다 새 limiter를 만들면 동시에 여러 Search가 들어왔을 때 configured limit이 곱해져
upstream-protection이라는 목적을 충족하지 못한다.

### Execution latency distinction

개별 HTTP response timeout과 Supplier 전체 execution deadline을 분리한다.

예:

```text
1000 Properties
→ 20 batches
concurrency = 5
response timeout = 2s
```

모든 batch가 timeout된다면 네 wave가 순차적으로 진행될 수 있으므로:

```text
response timeout = 2s
≠ entire Supplier execution finishes in 2s
```

이다.

현재 구현에서는 per-request timeout + Supplier-global bounded concurrency까지만 core로 구현한다.

전체 Supplier execution의 hard upper bound가 필요해지면 production evolution으로 별도의
Supplier-level execution deadline/budget을 추가하고, 완료된 batch는 보존하면서 unfinished /
not-yet-started batch를 중단하는 방향을 검토한다.

Concurrency 값을 크게 올리는 것으로 이 latency 문제를 해결하지 않는다.

### Outcome / Impact

- `maxBatchConcurrency=5`의 근거를 명시했다.
- limit을 Supplier별 / application-instance 범위의 shared limiter로 고정했다.
- concurrent Search 간에도 같은 Supplier limit을 공유하는 verification을 추가했다.
- `per-request timeout ≠ Supplier execution deadline`을 문서와 검증 계획에 반영했다.
- production tuning과 future execution deadline의 경계를 명확히 했다.

---

## Day 3 — Engineering Journal과 AI 활용 기록의 역할 분리

**Type:** Process Decision

### Context

설계 과정에서 AI를 여러 차례 활용하면서 `JOURNAL.md` 하나에 엔지니어링 판단과 AI 사용 내역을 모두
기록하면 문서가 설계 이력, AI transcript, 일일 작업 로그가 섞인 형태가 될 수 있다고 판단했다.

### Decision

프로세스 기록을 다음처럼 분리한다.

```text
Canonical docs
→ 현재 시스템의 정본

JOURNAL.md
→ 중요한 엔지니어링 판단, 문제, 대안, 결과

AI_USAGE.md
→ 중요한 AI 활용, 사람의 수용/수정/거부/보류 판단, 검증

Git history
→ 실제 변경 내역
```

`JOURNAL.md`와 `AI_USAGE.md`는 모두 event-driven으로 기록한다.

Routine CRUD, 파일 생성, dependency 추가, 포맷팅, 단순 boilerplate, commit 목록은 기록하지 않는다.

과거 AI 활용은 실제로 있었던 주요 decision cluster만 복원하며, 없었던 시행착오나 rejected suggestion을
평가 목적상 만들어내지 않는다.

### Why

평가자가 설계 의사결정의 흐름과 AI를 활용한 방식을 각각 빠르게 확인할 수 있고, canonical docs와
process history의 역할도 혼동되지 않는다.

### Outcome / Impact

- root에 `AI_USAGE.md`를 별도 유지한다.
- `JOURNAL.md`는 engineering history에 집중한다.
- README는 두 process 문서를 링크한다.
- `AGENTS.md`가 어떤 상황에 어느 process 문서를 갱신할지 명시한다.

---

## Day 3 — Public 문서를 외부 reference의 대체본으로 만들지 않기

**Type:** Process / Repository Decision

### Context

코드와 설계 문서는 public repository에서 충분히 설명되어야 하지만, project documentation이 외부
reference 문서를 섹션별로 다시 작성하거나 field dictionary/payload를 그대로 재구성하는 형태가 되면
구현 문서의 목적을 넘어설 수 있다.

또한 current tree에서 파일을 삭제해도 과거 commit에 남은 blob, filename, commit message, secret은
public Git history에서 계속 확인할 수 있다.

### Decision

`REQUIREMENTS.md`는 외부 provenance 문서가 아니라 **현재 project contract**로 유지한다.

Requirement classification:

```text
REQUIRED
PROJECT-MUST
PROJECT-DECISION
OPTIONAL
OUT-OF-SCOPE
HARD-CONSTRAINT
```

Public documentation 원칙:

```text
implementation / behavior
        ↓
engineering rationale
        ↓
public docs
```

외부 reference의 section structure를 그대로 따라가며 빠짐없이 재작성하는 방식은 사용하지 않는다.

`INTEGRATION.md`에서는 adapter correctness에 필요한 endpoint, authentication, request limit,
success/failure, price/inventory semantics는 유지한다. 반면 completeness만을 위한 exhaustive field
dictionary나 large reference payload reproduction은 만들지 않는다.

Repository safety는 두 범위를 모두 검사한다.

```text
current tracked tree
+
complete Git history
```

과거 commit에 restricted material이나 secret이 들어간 경우 later deletion만으로 완료 처리하지 않는다.

### Why

Public repository의 목적은 project implementation과 engineering judgment를 보여주는 것이다.
외부 reference를 다시 배포하지 않으면서도 코드가 왜 그렇게 동작하는지 설명하는 데 필요한 protocol
semantics는 충분히 남길 수 있다.

### Outcome / Impact

- provenance-specific requirement classification을 repository-neutral classification으로 교체했다.
- `REQUIREMENTS.md`를 project contract로 명확히 했다.
- `INTEGRATION.md`에 documentation boundary를 추가했다.
- `AGENTS.md` repository safety rule을 current tree + Git history 기준으로 강화했다.
- `VERIFICATION.md`에 documentation-reconstruction 및 full-history checks를 추가했다.

---

## Day 3 — Agent가 한 구현 Slice마다 증거와 판단을 드러내도록 작업 경계 설정

**Type:** Process / Agent Workflow Decision

### Context

AI agent가 문서를 읽고 프로젝트 전체를 자동 완성하는 방식보다, 한 번에 하나의 coherent implementation
slice를 구현하고 검증한 뒤 멈추는 방식이 이 프로젝트의 목적에 더 적합하다고 판단했다.

중간 checkpoint가 없으면 실제 구현 중 발견된 문제, trade-off, root cause, verification evidence가
최종 결과에 묻힐 수 있다. 반대로 파일/클래스마다 멈추면 작업 흐름이 지나치게 잘게 쪼개진다.

### Decision

Checkpoint 단위는 파일이 아니라:

```text
one coherent behavior
+
its owning verification
```

로 정의한다.

한 slice 완료 후 agent는 다음 major slice를 자동으로 시작하지 않고 다음을 보고한다.

```text
Requirement IDs
V-* IDs
implementation summary
engineering finding
verification evidence
JOURNAL / AI_USAGE candidate
commit boundary
next coherent slice
```

Routine implementation은 기록하지 않는다.

`JOURNAL.md`는 새로운 engineering finding이 있을 때만 후보가 되며, 이미 문서화된 설계 결정을 그대로
구현했다는 이유만으로 새 entry를 만들지 않는다.

`AI_USAGE.md`도 AI가 설계/구현전략/디버깅/검증에 의미 있게 영향을 준 경우만 기록한다.

Exact Requirement → `V-*` mapping은 구현 slice를 닫으면서 점진적으로 완성한다. 모든 미래 작업의
mapping을 구현 전에 억지로 만들지 않는다.

Commit boundary는 coherent behavior + verification 완료 시점으로 잡고, agent는 사용자 요청 없이
직접 commit하지 않는다.

### Why

이 방식은 agent의 구현 속도는 활용하면서도, 프로젝트에서 중요한 판단·문제 해결·검증 과정을
사람이 확인하고 이해한 상태로 남길 수 있다.

### Outcome / Impact

- `AGENTS.md`에 checkpoint/stop protocol을 추가했다.
- Journal/AI usage 후보 기준을 명확히 했다.
- Requirement→Verification mapping을 progressive하게 유지한다.
- 의미 있는 commit boundary 기준을 추가했다.
- canonical document 경로를 실제 `docs/` 구조와 일치시켰다.

## Day 4 — Catalog 검색 projection과 persistence 경계 정렬

**Type:** Problem / Decision

공개 조회를 실제 persistence 계층으로 연결하면서 Aggregate 전체를 읽어 application에서 필터링하는
방식이 canonical architecture의 projection 경계와 맞지 않음을 확인했다. 조회 서비스는
`SearchableCatalogReader` 포트만 의존하고, persistence adapter가 ACTIVE Property/RoomType projection을
제공하도록 정렬했다. persistence 모듈의 Spring Boot BOM 누락도 컴파일 단계에서 보완했다.

어댑터 본체와 Catalog application 컴파일은 통과했으며, Docker 미가용으로 PostgreSQL Testcontainers
통합 테스트는 아직 PASSING으로 올리지 않았다.

## Day 5 — Catalog 동기화 트랜잭션 경계 분리

**Type:** Decision / Verification

Supplier snapshot 조회와 persistence 반영을 분리했다. 조회·검증을 담당하는 서비스는 트랜잭션 없이
실행하고, `ApplyCatalogSnapshotService`만 동기식 `@Transactional` 메서드에서 reconcile과 저장을 수행한다.

PostgreSQL Testcontainers cross-module 테스트로 한 snapshot의 전체 롤백, Supplier 호출 중 트랜잭션 부재,
Supplier별 독립 커밋을 검증했다.

## Day 6 — Supplier A Catalog adapter BOM 정렬

**Type:** Problem / Decision

Supplier A Catalog 어댑터를 컴파일하면서 모듈에 Spring Boot BOM이 없어 기존 `spring-boot-starter-webclient`도
버전을 해석하지 못하는 문제를 확인했다. Supplier A 모듈에 BOM을 명시하고, adapter-local Jackson Kotlin
역직렬화를 통해 wire DTO를 Catalog-owned snapshot으로 변환하도록 구성했다.

제어 가능한 로컬 HTTP 스텁으로 정상 Catalog snapshot과 중복 Property 코드가 포함된 구조 오류의 fail-closed
동작을 검증했다.

## Day 7 — 연결 거부 테스트에서 포트 예약과 실제 거부의 차이

**Type:** Verification / Test-fixture correction

Supplier B Availability Adapter의 `CONNECTION_FAILED` 검증에서 `HttpServer.create(...)`로 포트만 예약하고
시작하지 않은 서버를 사용했다. 이 상태에서는 TCP 연결이 즉시 거부되지 않고 연결이 성립한 뒤 응답을
기다릴 수 있어 테스트가 대기했다.

테스트 fixture는 서버를 시작한 뒤 즉시 중지하도록 변경했다. 따라서 운영체제가 포트를 해제하고 WebClient가
실제 connection-refused 오류를 받으며, Adapter의 `CONNECTION_FAILED` 정규화를 결정적으로 검증한다.

이 수정 후 `:integration:supplier-b:cleanTest :integration:supplier-b:test`가 통과했고,
`V-RES-CONN-01`을 PASSING으로 갱신했다.

## Day 7 — Supplier B Catalog 검증 추적 보완

**Type:** Verification traceability

Supplier B Catalog의 complete snapshot 및 fail-closed 규칙은 canonical integration 계약에 있었지만,
검증 매트릭스에는 Supplier B Availability와 달리 이를 직접 소유하는 `V-*` 시나리오가 없었다.

정상 `0000` snapshot, 구조 오류의 partial-snapshot 금지, HTTP 200 + `E503`의 typed failure를
`V-INT-B-CAT-01..03`으로 명시했다. 이 변경은 계약 의미를 추가하지 않고, Supplier B Catalog Adapter의
구현·테스트·요구사항을 추적 가능하게 만든다.

## Day 7 — Composition Root의 직접 의존성 명시

**Type:** Architecture alignment

`:app`이 Supplier Availability Port map과 `ObjectMapper`를 직접 조립하는 runtime wiring을 추가하면서,
Supplier 모듈의 implementation 의존성이 App compile classpath에 노출될 것이라는 가정이 드러났다.
Composition Root가 사용하는 `:search:port`와 Jackson Kotlin 모듈을 `:app`에 직접 선언하고, Gradle
모듈 경계 허용 목록도 실제 책임과 일치시켰다. 이로써 outer composition이 concrete adapter 생성에
필요한 계약/기술 의존성을 명시적으로 소유한다.

## Day 7 — Netty timeout 예외의 명시적 정규화

**Type:** Defect / resilience verification

연결은 성공했지만 응답하지 않는 upstream을 100ms response timeout으로 호출한 결과, Netty는
`ReadTimeoutException`을 반환했다. 기존 Adapter는 Java `TimeoutException`만 검사해 이를
`CONNECTION_FAILED`로 분류했다.

timeout 원인 체인을 검사해 Java와 Netty timeout 예외를 모두 `TIMEOUT`으로 정규화했다. 실제
connected-but-stalled HTTP stub으로 수정 전 실패와 수정 후 통과를 확인했다.

## Day 8 — E2E가 발견한 Mock Supplier 응답 계약 불일치

**Type:** Defect / Test-fixture correction

정상 검색 E2E에서 Catalog 동기화와 readiness는 통과했지만 두 Supplier의 가용성 결과가 모두
`INVALID_RESPONSE`로 정규화됐다. 원인은 Mock Supplier 가용성 응답에 Catalog 전용 이름·수용 인원
필드가 함께 포함된 것이었다. Availability adapter의 wire DTO는 해당 필드를 계약으로 갖지 않으며,
엄격한 역직렬화가 이를 무효 응답으로 처리했다.

Mock 응답을 각 Supplier Availability payload 계약에 정의된 필드만 포함하도록 정정했다. 실제
`:mock-supplier`, PostgreSQL Testcontainers, `:app`을 함께 기동하는 E2E에서 startup Catalog sync,
영속된 mapping, `200 COMPLETE`, A/B live Offer를 확인했다.

## Day 8 — 독립 ApplicationContext E2E의 설정 우선순위

**Type:** Verification / Test-fixture correction

동일 PostgreSQL을 대상으로 애플리케이션을 두 번 기동하는 Catalog ID 안정성 E2E에서
`SpringApplicationBuilder.properties(...)`를 사용했다. 이는 기본값 우선순위여서
`application.yaml`의 미해결 `SNOWFLAKE_NODE_ID` placeholder보다 낮았고, Catalog bootstrap 전에
설정 바인딩이 실패했다.

테스트 전용 속성을 `MapPropertySource`의 최우선 환경 소스로 제공하도록 변경했다. 이로써 실제
Catalog bootstrap을 두 번 수행하고 동일 Supplier 외부 identity의 Property/RoomType 내부 ID가
그대로 유지됨을 검증했다.

## Day 8 — Spring 주입 생성자에 Kotlin 기본 인자 추가 시의 기동 실패

**Type:** Test-fixture correction

Mock Supplier에 Catalog 오류 모드를 추가하면서 Spring이 주입하는 주 생성자 끝에 Kotlin 기본 인자를
추가했다. 단위 테스트의 직접 생성은 통과했지만, 실행 jar에서는 Spring이 주입 가능한 생성자를 선택하지
못하고 기본 생성자를 찾으려 해 기동에 실패했다.

기본 인자를 제거하고 모든 생성자 인자를 Spring `@Value` 또는 Bean으로 명시했다. 직접 생성하는
테스트 fixture도 Catalog mode를 명시하도록 정렬했으며, fresh Catalog baseline 실패 E2E가 이를
재검증한다.

## Day 8 — 최종 빌드에서 발견한 테스트·산출물 배선 충돌

**Type:** Build / release verification

전체 `clean test`에서 별도 Mock Supplier 프로세스를 요구하는 `*EndToEndTest`가 일반 `:app:test`에도
포함되어, 전용 E2E 태스크만 제공하는 시스템 속성 없이 실행됐다. 일반 테스트는 E2E 클래스를 제외하고,
각 E2E 전용 태스크가 실제 Mock Supplier와 함께 실행하도록 역할을 분리했다.

이어진 `build`에서는 `:catalog:application`과 `:search:application`이 같은 `application.jar` 이름으로
Boot JAR에 포함되어 중복 항목 오류가 발생했다. 모든 모듈 archive 이름을 Gradle 프로젝트 경로 기반으로
고유화했다. 이후 전체 clean test와 build가 통과했다.

