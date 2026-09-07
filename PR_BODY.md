## Summary

PostgreSQL Catalog 제약 검증과 실제 Mock Supplier 기반 정상 검색 E2E 검증을 추가했습니다.

## Why

데이터베이스 무결성과 애플리케이션 조립 이후의 실제 검색 흐름을 실행 가능한 증거로 보장합니다.

## Changes

- Property/RoomType mapping의 중복 및 FK 제약을 PostgreSQL Testcontainers로 검증했습니다.
- 별도 `:mock-supplier` 프로세스를 기동하는 `:app:e2eTest` task를 추가했습니다.
- Startup Catalog 동기화, A/B mapping 영속, `200 COMPLETE` 및 live Offer 반환을 E2E로 검증했습니다.
- Mock Availability 응답을 Supplier별 wire DTO 계약과 일치시켰습니다.

## Key Decisions

`:app`이 `:mock-supplier`에 직접 의존하지 않도록, E2E에서 Mock Supplier를 별도 프로세스로 실행합니다.

## Verification

- `V-PER-CON-01..03`
- `V-E2E-01`
- `:catalog:adapter:persistence:test --tests com.staysupplierhub.catalog.adapter.persistence.CatalogPersistenceAdapterTest`
- `:mock-supplier:test`
- `:app:e2eTest`
- `verifyModuleBoundaries`

## Engineering Finding

Mock Availability 응답에 Catalog 전용 필드가 포함되면 엄격한 DTO 역직렬화가 정상 응답을 `INVALID_RESPONSE`로 처리했습니다. 가용성 payload를 각 Supplier 계약에 맞게 정리했습니다.

## Related Documentation

`docs/VERIFICATION.md`, `JOURNAL.md`, `AI_USAGE.md`를 실행 증거와 발견 사항에 맞게 갱신했습니다.
