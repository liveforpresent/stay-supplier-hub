# AI_USAGE.md

이 문서는 중요한 AI 지원 엔지니어링 작업을 기록합니다.

원문 프롬프트 기록이 아닙니다. 각 항목은 다음을 요약합니다.
- 무엇을 요청했는지,
- 중요한 제안은 무엇이었는지,
- 무엇을 수용·수정·거부·보류했는지,
- 최종 결정을 내린 이유,
- 결과를 어떻게 검증했는지 또는 검증할 것인지.

일상적인 자동 완성, formatting, 단순 boilerplate, 경미한 naming 제안은 제외합니다.
현재 시스템 truth의 source는 계속 canonical design document입니다.

---

## AI-001 — 요구사항 해석과 도메인 경계

**날짜:** 2026-09-03

### 맥락

프로젝트는 구현 전에 일관된 도메인 경계가 필요했다. 핵심 모호성은 Supplier integration 자체를 Bounded
Context로 둘지와 Catalog/Search를 어떻게 분리할지였다.

### 요청

- Catalog, Search, Supplier Integration의 가능한 Bounded Context 경계를 비교한다.
- Supplier Integration이 business context인지 ACL/outbound boundary인지 평가한다.
- Property/RoomType Aggregate 경계를 검토한다.

### AI 제안

- Catalog와 Search를 실제 business Bounded Context로 유지한다.
- Supplier Integration을 ACL/outbound adapter boundary로 둔다.
- `Property`를 Aggregate Root, `RoomType`을 Entity로 둔다.
- repository/entity 대신 좁은 Catalog Published Read Contract를 Search에 제공한다.

### 수용

- Catalog / Search Bounded Context 분리.
- Bounded Context 대신 지원 ACL로서의 Supplier Integration.
- RoomType Entity를 포함하는 Property Aggregate Root.
- 좁은 Catalog → Search Published Contract.

### 사람의 판단

Catalog는 안정적인 identity/lifecycle을 소유하고 Search는 일시적인 live offer를 소유하므로 분리를
수용했다. Supplier별 protocol은 독립적인 business lifecycle을 만들지 않으므로 business domain model
밖에 둔다.

### 검증

- module dependency rule은 `docs/ARCHITECTURE.md`에 문서화했다.
- boundary rule은 `docs/DOMAIN.md`, `docs/USE_CASES.md`에 문서화했다.
- static architecture verification scenario는 `docs/VERIFICATION.md`에서 유지한다.

---

## AI-002 — Catalog snapshot 조정과 수명주기

**날짜:** 2026-09-03

### 맥락

Supplier Catalog API는 complete snapshot을 반환하므로, 동기화에는 안정적인 내부 ID, 비활성화, 재활성화,
실패에 관한 명시적 의미가 필요했다.

### 요청

- 기존/신규/누락 Supplier 상품의 조정 규칙을 정의한다.
- 실패하거나 유효하지 않은 snapshot에서 삭제를 추론할 수 있는지 결정한다.
- 하나의 Supplier snapshot을 적용하는 transaction 범위를 검토한다.

### AI 제안

- 완전히 유효한 Supplier Catalog 응답을 complete snapshot으로 취급한다.
- 반복되는 identity의 내부 ID를 보존한다.
- 기존 항목이 누락되면 삭제 대신 INACTIVE로 표시한다.
- 다시 나타난 identity는 같은 내부 ID로 재활성화한다.
- 실패/무효 snapshot에서는 이전 상태를 보존하고 absence를 절대 추론하지 않는다.
- Supplier HTTP는 DB transaction 밖에서 수행하고, 하나의 유효 Supplier snapshot은 하나의 transaction으로 적용한다.

### 수용

위 항목 전체.

### 사람의 판단

Supplier identity는 안정적이고 Catalog API는 Supplier가 현재 처리하는 집합을 나타내므로 이 snapshot 정책을
선택했다. 실패는 absence의 증거가 아니므로 비활성화는 성공적이고 완전히 검증된 snapshot 이후에만 허용한다.

### 검증

- Catalog lifecycle scenario는 `docs/VERIFICATION.md`에서 추적한다.
- transaction/constraint 동작에는 PostgreSQL integration test가 필요하다.
- transaction 밖의 Supplier I/O는 architecture guardrail이다.

---

## AI-003 — Search outcome과 partial-failure 의미

**날짜:** 2026-09-03

### 맥락

통합 Search는 한 Supplier 또는 한 batch가 실패해도 유효한 결과를 보존해야 하며, 정상적인 빈 검색과
완료할 수 없었던 검색을 구분해야 했다.

### 요청

- complete / partial / unavailable 결과 의미를 정의한다.
- 항목 단위의 잘못된 데이터와 batch/Supplier 실패를 구분한다.
- 통합 결과를 public HTTP 상태에 어떻게 대응할지 결정한다.

### AI 제안

- 한 항목이 잘못되어도 유효한 같은 batch의 항목은 유지한다.
- 하나의 batch가 실패해도 성공한 batch는 유지한다.
- 관련 Supplier 실행 중 하나라도 성공하면 partial 결과를 반환한다.
- 관련 Supplier 실행이 모두 실패한 경우에만 unavailable을 반환한다.
- public 실패 세부 정보는 거칠게 유지하고 Supplier protocol/status 세부 사항은 노출하지 않는다.

### 수용

- partial 성공 결과 보존.
- batch/item 실패 구분.
- `200 COMPLETE/PARTIAL`과 `503 SEARCH_UNAVAILABLE`의 구분.
- 원본 upstream 세부 정보 없는 public degraded Supplier 목록.

### 수정

public contract는 내부 failure taxonomy보다 의도적으로 작게 유지했다. 내부 실패 이유는 진단에 사용할 수
있도록 남기되, API에는 degraded/unavailable Supplier 사실만 노출한다.

### 검증

- Application 및 web-contract 시나리오는 `docs/VERIFICATION.md`에서 관리한다.
- Supplier A HTTP 실패와 Supplier B 논리 실패는 독립적으로 테스트한다.

---

## AI-004 — Catalog bootstrap baseline과 Search readiness

**날짜:** 2026-09-03

### 맥락

의미상 공백이 발견됐다. 새로운 데이터베이스에서 Catalog 동기화가 실패하면 Search가 대상 0개를 보고
정상적인 빈 결과를 잘못 반환할 수 있었다.

### 요청

- 부분적인 Catalog 가용성에 대한 시작 정책을 비교한다.
- 정상적으로 비어 있는 Catalog와 bootstrap 실패로 알 수 없어진 Catalog를 구분한다.
- 이 관심사가 Search domain에 속하는지, application/operational state에 속하는지 결정한다.

### AI 제안

- Supplier별로 사용할 수 있는 Catalog baseline 개념을 둔다.
- 현재 동기화 성공이 baseline을 만든다.
- 동기화 실패 시 이전 persisted Catalog 상태를 stale baseline으로 사용할 수 있다.
- 이전 상태 없이 동기화가 실패하면 사용할 수 있는 baseline이 없다.
- Search 트래픽을 받기 전에 설정된 모든 Supplier에 baseline이 있도록 요구한다.
- bootstrap/readiness는 `SearchOutcome` 밖에 둔다.

### 수용

- `known empty Catalog != unknown Catalog`.
- 사용할 수 있는 baseline 정책.
- stale Catalog mapping fallback.
- 정상 Search 실행 전의 Search gate.
- `SearchOutcome.NOT_READY`는 두지 않음.

### 수정

readiness state만으로는 직접 endpoint 호출을 막기에 충분하지 않다고 판단했다. 최종 설계는 bootstrap gate가
닫혀 있는 동안 application/web 경로도 Search를 `503 SEARCH_UNAVAILABLE`로 거부하도록 한다.

### 보류

영속적인 Supplier Catalog 동기화 metadata 테이블은 보류했다. 현재 구현은 다음 동기화가 실패했을 때, 과거에
성공했으나 항상 비어 있던 Catalog와 한 번도 초기화되지 않은 Catalog를 재기동 후 구분할 수 없는 경계 사례를
수용한다.

### 검증

- bootstrap/readiness cross-module 시나리오는 `docs/VERIFICATION.md`에서 추적한다.
- gate가 닫혀 있을 때 직접 HTTP 호출은 200 empty가 아니라 503을 반환해야 한다.

---

## AI-005 — Supplier identity 표현과 확장 모델

**날짜:** 2026-09-03

### 맥락

Supplier routing은 `Map<SupplierId, Port>`를 사용한다. 이 표현은 Supplier A/B를 닫힌 domain 집합으로 만들지
않으면서 type safety를 제공해야 했다.

### 요청

- raw `String`, enum, sealed type, string-backed value class를 비교한다.
- Supplier C 확장의 영향을 평가한다.
- `SupplierId`의 소유 위치를 결정한다.

### AI 제안

- string-backed `@JvmInline value class`를 우선한다.
- `SupplierId`는 `:catalog:api`에 둔다.
- Composition Root 등록이 현재 지원 Supplier를 정의하게 한다.
- 시작 시 Supplier capability wiring을 검증한다.

### 수용

- `@JvmInline value class SupplierId(val value: String)`.
- non-blank 표현 invariant만 둠.
- Composition Root를 supported-Supplier registry로 사용.
- 누락되거나 불일치하는 Catalog/Availability adapter binding은 시작 시 fail-fast.

### 수정

중앙 `SupplierIds` registry는 채택하지 않았다. Supplier A/B 상수를 core domain type에 추가하지 않는다.

### 거부

- semantic type safety를 잃는 raw `String`.
- 공통 type이 지원 Supplier 집합을 소유하게 되는 닫힌 `enum class SupplierId { A, B }`.

### 검증

- static architecture review가 SupplierId 소유/표현을 확인한다.
- wiring test가 설정된 Supplier capability 불일치를 다룬다.
- identity type을 변경하지 않고 Supplier C를 표현할 수 있다.

---

## AI-006 — 통합 모델 정보 보존 정책

**날짜:** 2026-09-03

### 맥락

Supplier A는 일별 가격/세금 세부 정보를 제공하고 Supplier B는 숙박 전체 gross total만 제공한다. 공통 모델에는
정보 보존과 의도적 손실에 대한 명시적 규칙이 필요했다.

### 요청

- Supplier 정보를 보존, 도출, 검증 전용, 폐기 중 어디에 둘지 결정한다.
- 한 Supplier에 없는 field를 만들어 내지 않는다.
- Catalog와 live availability 응답 사이 metadata authority를 결정한다.

### AI 제안

field를 다음으로 분류한다.

```text
PRESERVE
DERIVE / NORMALIZE
VALIDATE THEN DROP
DISCARD
```

가장 풍부한 Supplier payload 대신, 사실에 맞는 가장 작은 공통 Search 의미를 사용한다.

### 수용

- 공통 가격 의미는 숙박 전체 total + currency.
- Supplier A의 일별 가격/세금은 도출에만 사용하고 공통 Search 모델에서는 생략.
- Supplier B `taxIncluded`는 gross 의미 검증에 사용한 뒤 제거.
- Supplier B의 일별/세금 세부 정보는 절대 만들어 내지 않음.
- 일별 inventory는 일시적으로 유지한 뒤 숙박 전체 availability로 축약.
- 이름/max occupancy는 Catalog가 authority를 유지.
- 외부 Supplier code와 raw protocol error는 public API 밖에 둠.

### 사람의 판단

정보 손실은 downstream Search가 그 세부 정보를 필요로 하지 않고 다른 Supplier도 동등한 의미를 사실대로
제공할 수 없는 경우에만 수용했다.

### 검증

- Supplier normalization test가 A/B 가격 의미를 검증한다.
- Search test가 Catalog metadata authority를 검증한다.
- web-contract test가 외부 code/일별 가격 내역/raw error가 public으로 새지 않음을 검증한다.

---

## AI-007 — 추측성 모델링 없는 확장 도메인 범위

**날짜:** 2026-09-03

### 맥락

설계는 근거 없는 Reservation/Payment/Pricing 모델을 만들지 않으면서 더 넓은 관련 domain을 인식하고 있음을
보여야 했다.

### 요청

- core flow만 구현하면서 더 넓은 domain을 설계하라는 요구를 해석한다.
- Bounded Context를 미리 만들지 않고 인접 capability를 문서화할 방법을 결정한다.
- business-domain 진화와 infrastructure 진화를 분리한다.

### AI 제안

범위를 다음처럼 구성한다.

```text
현재 핵심
인접 domain capability
infrastructure / integration 진화
명시적으로 모델링하지 않은 product concept
명시적인 범위 밖
```

고정된 미래 tactical model 대신 boundary trigger를 문서화한다.

### 수용

- Catalog/Search만 현재 business Bounded Context로 유지.
- Canonicalization, Reservation, currency conversion은 재검토 trigger가 있는 인접 capability.
- retry/circuit-breaker/cache/quarantine은 integration/infrastructure 진화 영역.
- Physical Room, persistent RatePlan, persistent Search History는 identity/lifecycle 요구 없이 모델링하지 않음.
- 명시적 OOS는 별도로 유지.

### 거부

구체적 요구 없이 추측성 `Reservation`, `Pricing`, `Canonicalization` 또는 유사 Gradle module/Bounded Context를
미리 설계하는 방식.

### 검증

- `docs/DOMAIN.md`에 범위/진화 지도가 있다.
- architecture 문서는 실제 module로 한정한다.
- 문서 검토에서 미래 BC가 성급하게 고정되지 않았는지 확인한다.

---

## AI-008 — 대규모 batching과 제한된 동시성

**날짜:** 2026-09-03

### 맥락

Supplier availability API는 요청 하나당 최대 50개의 Property code만 받는다. 수천 규모에서 batching 전략,
fan-out 보호, latency 의미에는 명시적인 판단이 필요했다.

### 요청

- 초기 batch-concurrency 값을 정당화한다.
- concurrency limiter가 Search 요청별인지 Supplier 전체인지 결정한다.
- 요청별 timeout과 전체 Supplier 실행 latency의 관계를 분석한다.

### AI 제안

- `maxBatchConcurrency=5`를 보수적이고 설정 가능한 초기값으로 유지한다.
- 이를 최적값/SLA/protocol 상수가 아니라 tuning 기본값으로 취급한다.
- 같은 Supplier adapter instance를 사용하는 모든 Search가 limiter 하나를 공유한다.
- 요청별 timeout과 전체 Supplier 실행 latency를 구분한다.
- Supplier-level execution deadline/budget은 production 진화 항목으로만 고려한다.

### 수용

- 기본값 `5`.
- application instance별 Supplier-global limit.
- `50 x 5 = Supplier별 활성 outbound batch 요청에서 최대 250`개의 Property target.
- Supplier별 독립 설정.
- latency, timeout, rate-limit, failure metric에 따른 production tuning.
- `per-request timeout != Supplier execution deadline`.

### 보류

Supplier-level execution deadline/budget, fairness, distributed/global rate limiting, admission control은
production 규모에서 필요해질 때까지 보류한다.

### 검증

- concurrency test는 여러 동시 고객 Search 전체의 in-flight 요청 수를 관찰해야 한다.
- configuration binding이 기본값 `5`를 검증한다.
- 검증은 하나의 response-timeout 구간을 전체 Supplier latency라고 주장하지 않는다.

---

# 기록 유지 규칙

AI가 다음 중 하나에 실질적으로 영향을 준 경우에만 새 항목을 추가한다.

- architecture/domain 판단,
- implementation 전략,
- 자명하지 않은 debugging/root-cause 분석,
- transaction/concurrency/resilience 판단,
- verification/test 전략,
- 의미 있는 범위 trade-off.

일상적인 formatting, syntax completion, 단순 boilerplate, commit message 문구, 일반 파일 생성은 기록하지 않는다.

사후에 거부한 아이디어나 실패한 구현을 만들어 내지 않는다. 실제로 일어난 판단과 사건만 기록한다.

---

## AI-009 — 공개 repository 문서 경계

**날짜:** 2026-09-03

### 맥락

repository에는 상세한 프로젝트 소유 설계 문서가 필요하지만, 프로젝트 외부 reference material을 재배포하거나
사실상 복원해서는 안 된다.

### 요청

- 정당한 implementation 문서와 외부 reference 재배포의 경계를 정의한다.
- provenance-specific requirement class가 public repository에 남아야 하는지 검토한다.
- 현재 working tree를 넘어 repository 검사를 강화한다.

### AI 제안

- `REQUIREMENTS.md`를 외부 provenance의 복제본이 아닌 프로젝트의 현재 contract로 다룬다.
- provenance-specific 분류를 repository-neutral 프로젝트 분류로 바꾼다.
- adapter 설명에 필요한 protocol 사실은 유지하되, 외부 field dictionary/reference payload의 포괄적 재현은 피한다.
- 현재 tree뿐 아니라 전체 Git history를 검사한다.
- 제한 자료나 secret이 history에 들어갔다면 이후 삭제만 하는 commit 대신 history rewrite로 제거한다.

### 수용

- provenance-specific requirement class를 repository-neutral `REQUIRED`, `OPTIONAL`, `OUT-OF-SCOPE` class로 교체.
- public 문서는 구현된 behavior/design에서 출발해 작성.
- protocol semantics가 구현 behavior를 결정하는 곳에서는 `INTEGRATION.md`의 상세함을 유지.
- 현재 tree와 전체 history repository safety check를 모두 필수로 함.

### 수정

integration 문서를 모호한 설명으로 축소하지 않았다. 구현된 adapter behavior를 실질적으로 정의하는 정확한
endpoint, limit, authentication, failure/price semantics는 계속 문서화한다.

### 사람의 판단

repository는 외부 reference material의 대체 복제본이 되지 않으면서도, 독립적인 engineering project로 이해할
수 있어야 한다. implementation artifact와 프로젝트가 작성한 판단은 유지하고, provenance-specific 문구와
불필요한 원문 복원은 제거한다.

### 검증

- `docs/VERIFICATION.md`에 current-tree 및 Git-history review 시나리오가 있다.
- 문서 검토에서 재구성된 외부 field dictionary/payload를 확인한다.
- 조립된 package에 public-artifact restricted-string scan을 실행한다.

---

## AI-010 — Agent checkpoint와 엔지니어링 기록 workflow

**날짜:** 2026-09-03

### 맥락

원하는 workflow는 agent가 전체 project를 자율적으로 끝내는 방식이 아니다. 구현은 일관된 slice로 진행하고,
의미 있는 engineering 판단, 문제, AI-assisted reasoning을 자연스러운 checkpoint에서 드러내야 한다.

### 요청

- implementation 시작 전에 기존 agent harness에 더 필요한 것을 식별한다.
- agent가 전체 project를 자동으로 이어서 진행하지 못하게 한다.
- engineering work가 Journal/AI 기록의 대상이 되는 때를 정의한다.
- 파일 단위 commit 없이 의미 있는 commit history를 보존한다.
- 코딩 전 전체 Requirement→Verification mapping을 완료해야 하는지 결정한다.

### AI 제안

- 하나의 일관된 implementation slice 뒤에 checkpoint/stop rule을 둔다.
- Requirement ID, `V-*` ID, evidence, engineering finding, record candidate, commit boundary를 보고한다.
- `JOURNAL.md`와 `AI_USAGE.md`에는 event-driven criteria를 사용한다.
- slice를 닫으면서 정확한 Requirement→`V-*` mapping을 점진적으로 만든다.
- 일관된 behavior+verification commit을 추천하되 자동 수행하지 않는다.

### 수용

다섯 가지 workflow 변경을 모두 수용했다.

### 사람의 판단

agent는 개발자의 판단 과정을 숨기지 않으면서 implementation leverage를 극대화해야 한다. 따라서 workflow는
의미 있는 engineering boundary에서 멈추고, evidence와 자명하지 않은 finding을 공개하며, 다음 주요 slice는
명시적인 사용자 판단에 맡긴다.

### 검증

- `AGENTS.md`에 checkpoint/stop, record-candidate, progressive traceability, commit-boundary rule이 있다.
- `docs/VERIFICATION.md`에 agent-harness review 시나리오가 있다.
- `AGENTS.md`의 canonical document path는 `docs/...` file로 연결된다.

## AI-011 — Catalog persistence projection 경계

**날짜:** 2026-09-05

Aggregate 전체 조회를 사용하는 공개 조회 구현을 canonical architecture에 맞는
`SearchableCatalogReader` projection 경계로 수정했다. persistence 모듈 컴파일에서 발견된 Spring Boot
BOM 누락도 함께 보완했다.

검증 결과 Catalog persistence/application 컴파일은 통과했으며, Docker 미가용으로 Testcontainers
통합 테스트 실행은 보류했다.

## AI-012 — Catalog 동기화 transaction 경계

**날짜:** 2026-09-05

Supplier I/O와 JPA 반영을 분리하는 `ApplyCatalogSnapshotService` 구조와 PostgreSQL cross-module 검증을
구현했다. snapshot 롤백, 외부 호출 중 트랜잭션 부재, Supplier별 독립 커밋이 자동 테스트로 통과했다.

## AI-013 — Catalog runtime composition과 Kotest 도입

**날짜:** 2026-09-05

사용자 요청에 따라 기존 JUnit 5 테스트를 전환하지 않고, 새 카탈로그 런타임 조립 검증에 Kotest를
점진 도입했다. `:app`에서 Snowflake 기술 구현을 Catalog ID Generator Port로 바인딩하고, application
서비스 조립을 검증하는 방향을 선택했다.

## AI-014 — Supplier A Catalog adapter 검증

**날짜:** 2026-09-06

승인된 Supplier A wire contract를 바탕으로 Catalog adapter와 로컬 HTTP 스텁 검증을 구현했다. AI가
WebClient의 Kotlin DTO 역직렬화 경로와 모듈 BOM 누락을 진단했고, 명시적 Jackson Kotlin mapper를 사용해
wire DTO를 adapter 내부에 유지하면서 neutral Catalog snapshot으로 정규화하는 방안을 제안했다.

## AI-015 — Search application 결과 조립

**날짜:** 2026-09-06

AI 지원 구현으로 승인된 Search application slice를 완료했다. 수용한 방식은 `StayOffer`를 Search-domain
값으로 유지하고, `SearchStaysUseCase`와 `SearchOutcome`은 Search inbound Port로 노출한다. application은
Catalog의 published contract만 읽고, structured coroutine으로 Supplier group을 실행하며, 외부 code를
Catalog identity에 연결하고 partial 결과를 보존한다.

구현은 mapping되지 않았거나 occupancy가 맞지 않거나 inventory가 잘못된 항목에 `INVALID_RESPONSE` 사실을
기록하되 유효한 같은 결과의 항목은 버리지 않는다. Supplier Port에는 경쟁하는 metadata가 없으므로,
Catalog의 name과 occupancy만 metadata source로 유지한다.

검증: `:search:application:cleanTest :search:application:test :search:application:check`가 통과했고,
두 Supplier group이 release 전에 모두 진입함을 보이는 CountDownLatch barrier를 포함해 application test
10개가 통과했다. `verifyModuleBoundaries`도 통과했다.

## AI-016 — Supplier B availability 요청 변환

**날짜:** 2026-09-06

AI 지원 구현으로 Search-owned availability Port 경계 아래 Supplier B 요청 client를 추가했다. 제공된 숙박
기간과 투숙객 구성을 보존하고, 쉼표로 구분한 `propertyIds`를 전송하며, 50개 target 요청 제한을 강제하고,
Supplier-specific HTTP 세부 사항은 adapter 안에 둔다. 로컬 HTTP stub가 API key와 요청 변환을 검증했고,
focused test 4개가 모두 통과했다.

## AI-017 — Supplier B protocol과 가격 정규화

**날짜:** 2026-09-06

AI 지원 구현으로 HTTP 200을 transport 성공으로만 취급하고, 데이터가 Search에 들어오기 전에 body-level
`resultCode`를 해석하는 Supplier B 응답 normalizer를 추가했다. 알려진 Supplier code는 Search-owned failure
taxonomy로 mapping한다. 성공 데이터는 숙박 전체 `totalPrice`를 보존하고, 일별 또는 세금 내역을 만들어 내지
않으며 `taxIncluded=true`를 요구한다. focused normalization test 8개가 통과했다.

## AI-018 — Supplier B adapter와 connection-failure 검증

**날짜:** 2026-09-06

AI 지원 구현으로 Supplier B HTTP client와 normalizer를 Search-owned availability Port로 조립했다. adapter는
WebClient 실패를 상위로 노출하지 않고 HTTP status, body-level `resultCode`, 잘못된 요청 크기, 연결 거부를
정규화한다. 검증 중 bound만 되어 있고 시작하지 않은 HTTP server가 TCP 연결을 수락해 의도하지 않은 대기가
발생했다. fixture는 이제 요청 전에 server를 시작하고 중지하여 port가 실제로 거부되게 한다. clean Supplier B
test run에서 17개 test가 통과했다.

## AI-019 — Supplier B batch 보존

**날짜:** 2026-09-06

AI 지원 구현으로 Supplier B adapter를 단일 요청 처리에서 Supplier-owned batching으로 확장했다. target은
integration 요청 제한에서 나뉘고, 공유되는 per-adapter semaphore가 Search에 제한을 인코딩하지 않고 설정된
상한을 제공한다. batch outcome을 병합하여 다른 batch가 실패해도 성공 항목을 사용할 수 있게 한다. focused
HTTP-stub test는 50, 51, 121 target 형태와 중간 batch 실패 시 성공 결과 보존을 검증했다.

## AI-020 — Supplier B 공유 batch 동시성 검증

**날짜:** 2026-09-06

AI 지원 검증으로 Supplier B adapter에 제어 가능한 in-flight HTTP stub을 추가했다. CountDownLatch barrier는
경과 시간 assertion에 의존하지 않고 설정 상한, 하나 초과의 활성 요청, 동시 Search 호출 사이 limiter 하나의
공유, 기본 상한 5를 증명한다. 추가 후 focused Supplier B test suite가 통과했다.

## AI-021 — Supplier B Catalog adapter

**날짜:** 2026-09-06

AI 지원 구현으로 Catalog-owned Port 뒤에 Supplier B Catalog adapter를 추가했다. body-level `resultCode`를
변환하고 유효한 빈 complete snapshot을 보존하며, 누락 또는 중복된 Catalog 구조에는 fail-closed하여 partial
snapshot이 reconciliation에 도달하지 않게 한다. 로컬 HTTP-stub test는 정상 mapping, 빈 snapshot 의미,
구조 실패, body-level `E503` 정규화를 검증했다.

## AI-022 — Runtime Supplier 설정과 wiring

**날짜:** 2026-09-06

사용자가 승인한 map 기반 `supplier-integration.suppliers` 계약을 바탕으로, AI가 `:app` Composition
Root에 설정 바인딩, Supplier별 WebClient 생성, 명시적 Catalog/Availability Port map 조립, 그리고
fail-fast 집합 일치 검증을 구현했다. 기존 wiring ID의 의미를 보존하고 독립 설정 바인딩과 직접
SupplierId 변환에는 `V-WIRE-SUP-06..07`을 새로 배정했다.

실행 가능한 wiring 테스트도 추가했지만, 이 작업 환경의 Gradle 클라이언트가 task 로그와 결과 파일을
남기지 않은 채 종료되어 아직 실행 증거를 얻지 못했다. 따라서 관련 `V-WIRE-SUP-*` 상태는 PLANNED로
유지한다.

후속으로 persistent Gradle daemon을 사용해 runtime wiring focused test를 통과시켰다.
`V-WIRE-SUP-01..03`, `V-WIRE-SUP-07`은 PASSING으로 갱신했으며, 독립 설정값의 실제 adapter 동작
검증인 `V-WIRE-SUP-06`은 별도 시나리오로 유지한다.

## AI-023 — Supplier별 runtime 설정 격리 검증

**날짜:** 2026-09-06

AI가 A/B에 서로 다른 로컬 HTTP stub URL과 API key를 주입한 실제 Spring Composition Root 검증을
추가했다. 각 Availability Port가 자신의 endpoint와 credential만 사용함을 확인해
`V-WIRE-SUP-06`을 PASSING으로 갱신했다.

## AI-024 — Netty response timeout 정규화

**날짜:** 2026-09-06

AI가 connected-but-stalled HTTP stub과 100ms test-only response timeout을 사용해 Supplier A Availability
Adapter를 검증했다. 실제 Netty `ReadTimeoutException`이 기존 Java `TimeoutException` 분기에서 누락되어
`CONNECTION_FAILED`로 잘못 정규화되는 결함을 확인했고, 원인 체인에서 두 timeout 유형을 모두
`TIMEOUT`으로 정규화하도록 수정했다.

## AI-025 — 정상 검색 E2E와 Mock 응답 계약 정렬

**날짜:** 2026-09-07

AI가 실제 `:mock-supplier`, PostgreSQL Testcontainers, `:app` HTTP 서버를 함께 기동하는 정상 검색
E2E를 구성했다. 실행 중 Catalog bootstrap은 성공했지만 A/B 가용성 응답이 `INVALID_RESPONSE`가 되는
문제를 분석했고, Mock 응답에 Availability wire DTO가 소유하지 않는 Catalog 전용 필드가 섞여 있음을
확인했다. Mock payload를 Supplier별 Availability 계약으로 정렬한 뒤 `200 COMPLETE`, 두 live Offer,
내부 ID 노출, 영속된 A/B mapping을 자동 검증해 `V-E2E-01`을 PASSING으로 갱신했다.

## AI-026 — 재기동 Catalog ID 안정성 E2E

**날짜:** 2026-09-07

AI가 하나의 PostgreSQL Testcontainer와 실제 Mock Supplier를 공유하는 두 ApplicationContext 기동
E2E를 구성했다. 초기에는 Builder 기본 속성이 `application.yaml`의 미해결 환경변수보다 낮은 우선순위를
가져 Catalog bootstrap이 시작되기 전에 실패했다. AI가 원인을 설정 소스 우선순위로 분리하고 테스트
속성을 최우선 `MapPropertySource`로 변경했다.

수정 후 두 번의 Catalog 동기화가 모두 성공했고, 동일 Supplier 외부 identity의 Property와 RoomType
공개 내부 ID가 동일함을 검증해 `V-E2E-08`을 PASSING으로 갱신했다.

## AI-027 — Fresh Catalog baseline 실패 E2E

**날짜:** 2026-09-07

AI가 Supplier A Catalog HTTP 오류와 Supplier B 정상 Catalog을 분리 주입하는 Mock 모드 및 fresh DB
E2E를 구성했다. 초기 Mock 확장에서 Kotlin 기본 생성자 인자로 인해 Spring 실행 jar가 주입 생성자를
선택하지 못하는 문제를 확인했고, 기본 인자를 제거해 모든 주입 인자를 명시적으로 제공하도록 수정했다.

실제 E2E는 A persisted state가 비어 있고 B mapping만 저장된 상태에서 readiness gate가 닫히며,
Search가 빈 `200`이 아닌 A-only `503 SEARCH_UNAVAILABLE`을 반환함을 검증해 `V-E2E-09`을 PASSING으로
갱신했다.

## AI-028 — 제출 전 최종 Gradle 게이트 정상화

**날짜:** 2026-09-07

### 맥락

제출 전 전체 Gradle 게이트가 일반 테스트 태스크와 Boot JAR 조립에서 모두 통과해야 했다.

### 요청

AI는 `clean test`와 `build`를 실행하고, 실패가 있으면 새 기능을 추가하지 않고 제출 게이트만
정상화하도록 요청받았다.

### AI 제안

실패 원인을 전용 Mock Supplier가 필요한 E2E 클래스의 일반 `:app:test` 포함과, 두 중첩 모듈의
동일한 `application.jar` archive 이름으로 분리했다.

### 처리 결과

MODIFIED

### 사람의 판단

사용자는 추가 구현보다 제출 가능 증거를 우선했다. 이에 일반 테스트에서는 `*EndToEndTest`를 제외하고
전용 E2E 태스크만 해당 클래스를 실행하게 했으며, archive 이름은 Gradle 프로젝트 경로 기반으로
고유화했다.

### 결과

일반 단위·통합 테스트와 외부 Mock Supplier를 요구하는 E2E 테스트의 실행 책임이 분리됐고, Boot JAR가
중복 library 항목 없이 조립됐다.

### 검증

23개 테스트 리포트의 149개 테스트가 실패·오류 없이 통과했고, `./gradlew.bat build`가
`BUILD SUCCESSFUL`로 완료됐다.

