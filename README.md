# stay-supplier-hub

여러 Supplier의 숙소 Catalog와 실시간 가용성을 통합하는 서비스입니다.

> 상세 설계 문서는 `docs/`에 있습니다.

## 실행 흐름

애플리케이션은 시작 시 설정된 각 Supplier Catalog를 PostgreSQL에 동기화합니다. Search에서는 Catalog의
메타데이터와 안정적인 내부 ID를 실시간 가용성 결과에 결합합니다.

```text
HTTP Search
→ 준비 상태 게이트
→ 검색 가능한 Catalog projection
→ Supplier A/B 가용성 동시 호출
→ Supplier별 정규화와 제한된 배치 처리
→ Catalog 결합과 전체 숙박 가용성 계산
→ COMPLETE, PARTIAL 또는 SEARCH_UNAVAILABLE 응답
```

설정된 Supplier 중 하나라도 사용할 수 있는 Catalog baseline이 없으면 준비 상태 게이트는 닫힌 채로
유지됩니다. 갱신 실패는 이미 확립된 baseline을 닫지 않습니다. Search는 저장된 Catalog 메타데이터를
계속 사용하고, 실시간 가용성의 책임은 Supplier에 남습니다.

## 빌드, 테스트, 실행

사전 조건은 JDK 25, 테스트 중 PostgreSQL Testcontainers를 위한 Docker, 로컬 애플리케이션 실행을 위한
PostgreSQL입니다.

```powershell
.\gradlew.bat clean test
.\gradlew.bat build
```

로컬 실행 시에는 먼저 별도 Mock Supplier를 시작합니다.

```powershell
.\gradlew.bat :mock-supplier:bootRun
```

다른 터미널에서 PostgreSQL과 Snowflake 설정을 제공한 뒤 애플리케이션을 시작합니다. Supplier A/B는 URL과
API key를 외부에서 제공하지 않으면 로컬 Mock Supplier 기본값을 사용합니다.

```powershell
$env:SNOWFLAKE_NODE_ID = "1"
$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:5432/stay_supplier_hub"
$env:SPRING_DATASOURCE_USERNAME = "<database-user>"
$env:SPRING_DATASOURCE_PASSWORD = "<database-password>"
.\gradlew.bat :app:bootRun
```

Search 엔드포인트는 다음과 같습니다.

```text
GET /api/v1/stays/search?checkIn=2026-09-01&checkOut=2026-09-04&adults=2&children=0
```

공개 요청/응답 계약은 [`docs/API.md`](docs/API.md)에 정의합니다. E2E 검증은 별도 Mock Supplier 프로세스를
대상으로 전용 `:app:e2eTest` 및 시나리오별 `:app:e2e*Test` 태스크에서 실행합니다.

## 범위

### 현재 핵심

- **Catalog** — 안정적인 Supplier 기반 Property/RoomType identity, mapping, 메타데이터, 동기화.
- **Search** — 실시간 전체 숙박 가격, 가용성, offer 조건, 통합 결과 의미.
- **Supplier Integration** — Supplier별 프로토콜과 실패를 위한 ACL/outbound adapter 경계.

### 향후 확장을 고려한 영역

추측성 Bounded Context를 미리 만들지 않고 다음의 더 넓은 문제 영역을 고려합니다.

- cross-Supplier Property canonicalization,
- Reservation / Booking lifecycle,
- common-currency conversion,
- retry / circuit breaker,
- price & inventory cache,
- normalization quarantine.

이 영역은 경계를 올바르게 정의하는 데 필요한 identity, lifecycle, consistency 또는 운영 정책을 구체적
요구사항이 도입할 때만 다시 검토합니다.

### 명시적 범위 밖

- end-user authentication / authorization,
- payment integration,
- administrator features,
- frontend,
- real commercial Supplier APIs,
- region / keyword search,
- sorting / pagination.

범위 판단 근거와 경계 재검토 조건은 `docs/DOMAIN.md#8-domain-scope--evolution`에서 확인할 수 있습니다.

## 아키텍처 결정

- **실시간 Search 이전의 Catalog** — PostgreSQL은 안정적인 Supplier 기반 Property/RoomType identity와
  메타데이터를 유지합니다. 실시간 가용성은 Catalog 상태를 변경하지 않으며 Search 중 Supplier 외부 code로만
  결합합니다.
- **Spring MVC inbound, WebClient outbound** — inbound HTTP는 일반적인 Spring MVC로 유지하고 Supplier I/O는
  WebClient로 수행합니다. blocking JPA 작업은 Supplier network 대기와 분리합니다.
- **Supplier별 ACL** — A/B 프로토콜 DTO, 인증, 실패 해석, 가격 변환은 각 integration module에 둡니다.
  Search는 consumer-owned Port를 통해 정규화된 상업 결과만 받습니다.
- **개방형 Supplier identity** — `SupplierId`는 문자열 value object입니다. runtime configuration이 활성
  Supplier key set을 제공하고, 애플리케이션은 fail-fast 완전성 검증과 함께 `Map<SupplierId, Port>` binding을
  명시적으로 조립합니다.
- **만들어 낸 성공보다 partial result** — Supplier 또는 batch 실패가 발생해도 성공한 독립 결과를 보존하고
  성능이 저하된 Supplier를 보고합니다. 관련 Supplier가 모두 실패하면 공개 endpoint는
  `503 SEARCH_UNAVAILABLE`을 반환합니다.

정확한 계약은 [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md),
[`docs/USE_CASES.md`](docs/USE_CASES.md), [`docs/INTEGRATION.md`](docs/INTEGRATION.md)에서 확인할 수 있습니다.

## 배치와 동시성

Supplier 가용성 API는 요청당 최대 50개의 Property code를 받습니다. 더 큰 target set은 50-Property batch로
나누고 제한된 동시성으로 실행합니다.

초기 기본값:

```text
max batch concurrency per Supplier / application instance = 5
```

`5`는 측정된 최적값이나 Supplier SLA가 아닌 보수적인 시작값입니다. 순차 실행은 피하면서 한 애플리케이션
instance가 무제한 fan-out을 만들지 않게 합니다. 요청당 50개 Property 기준으로 활성 batch call이 최대
5개이면 한 Supplier에 대해 최대 250개 Property target이 동시에 처리됩니다.

limiter는 같은 Supplier adapter instance를 사용하는 모든 동시 고객 Search가 공유합니다. Search 요청마다
새로 만들지 않습니다.

값은 Supplier별로 독립 설정하며 latency, timeout, rate-limit, failure metric에 따라 조정해야 합니다.

요청별 timeout과 전체 Supplier 실행 latency는 의도적으로 구분합니다.

```text
2s response timeout
≠
2s total Supplier execution deadline
```

대기 중인 여러 batch는 여러 timeout wave를 필요로 할 수 있습니다. 현재 구현은 고정된 end-to-end Supplier
latency bound를 주장하지 않습니다. scale이 hard bound를 요구하면 Supplier-level execution deadline/budget을
production evolution으로 추가할 수 있습니다.

## 통합 모델 — 정보 정책

Supplier payload를 큰 공통 superset으로 복사하지 않습니다. 서비스는 안정적인 공통 의미가 있는 정보를
보존하고, Supplier 표현이 다른 값은 공통 값으로 계산하며, 일부 field는 validation에만 사용하고,
프로토콜별 detail은 의도적으로 제거합니다.

| 정보 | 정책 | 이유 |
|---|---|---|
| Supplier Property/Room code | 내부 보존 | 안정적인 mapping과 live-response join |
| 내부 Property/Room ID | 보존 | 안정적인 내부/공개 identity |
| Property/Room 이름, maxOccupancy | Catalog에서 보존 | 단일 메타데이터 authority |
| Supplier A nightly price/tax | 계산 후 제거 | 전체 숙박 gross total 계산에 사용 |
| Supplier B totalPrice | 보존 | 이미 전체 숙박 gross total |
| Supplier B taxIncluded | 검증 후 제거 | gross-price 의미 확인 |
| 통화 | 보존 | 암묵적 FX conversion 금지 |
| 일별 inventory | 일시 보존 후 계산 | 전체 숙박 availability에 필요 |
| availableRooms | 계산 후 보존 | 공통 전체 숙박 availability |
| breakfastIncluded | 보존 | Search 의존 상업 조건 |
| Availability 응답의 이름/occupancy | authority로는 제거 | Catalog가 메타데이터 authority로 유지 |
| Raw Supplier 오류 | 정규화 후 공개적으로 숨김 | 프로토콜 누출 방지 |
| 공개 Search API의 외부 code | 제거 | 공개 계약은 내부 ID 사용 |
| 공개 API의 nightly/tax breakdown | 제거 | 모든 Supplier에서 사실대로 제공할 수 없음 |

공통 가격 의미:

```text
whole-stay customer-facing total + currency
```

누락된 Supplier 정보는 절대 만들어 내지 않습니다.

일별 inventory는 내부 validation/calculation input으로 유지합니다. 공개 API는 요청한 전체 숙박에 대해
계산된 이용 가능 객실 수만 노출합니다.

상세 ownership과 normalization rule은 `docs/INTEGRATION.md`, `docs/DOMAIN.md`에서 확인할 수 있습니다.

## 엔지니어링 프로세스

process record는 normative design document와 분리해 유지합니다.

- [`JOURNAL.md`](JOURNAL.md) — 의미 있는 엔지니어링 결정, 문제, 대안, 결과.
- [`AI_USAGE.md`](AI_USAGE.md) — 중요한 AI 지원, 수용/수정/거부/보류한 내용, 결과 검증 방법.

canonical design truth는 `docs/`에 유지하며 Git history는 정확한 repository 변경을 기록합니다.
