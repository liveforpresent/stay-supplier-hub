## Summary

핵심 검색 흐름의 Supplier 실패·재고·배치·Catalog baseline E2E 검증을 확장하고, 제출 전 전체 Gradle 빌드 게이트를 정상화했습니다.

## Why

정상 검색만으로는 Supplier 장애, 빈 초기 Catalog, stale baseline, 재고 최소값과 배치 요청의 실제 동작을 보장할 수 없습니다. 또한 일반 테스트와 E2E 테스트의 실행 경계 및 Boot JAR 조립이 최종 빌드를 막지 않아야 합니다.

## Changes

- A HTTP 오류, B body-level 오류, A timeout, 전체 Supplier 실패의 부분·불가용 응답을 E2E로 검증했습니다.
- zero inventory, 51개 A Property 배치, Catalog ID 안정성, fresh/stale Catalog baseline을 E2E로 검증했습니다.
- Mock Supplier에 필요한 오류·재고·대량 Catalog fixture를 추가했습니다.
- Search 애플리케이션에서 Catalog 메타데이터 우선과 다일 재고 최소값을 검증했습니다.
- 일반 `:app:test`와 전용 E2E 태스크를 분리하고, 중첩 모듈 archive 이름을 고유화했습니다.

## Key Decisions

E2E는 외부 프로세스로 실행되는 Mock Supplier와 함께 전용 태스크에서만 실행합니다. 일반 `test` 게이트는 외부 프로세스 의존 없이 반복 가능해야 하며, E2E 증거는 별도 태스크가 소유합니다.

## Verification

- `V-E2E-02..10`
- `V-MCK-09..12`
- `V-INFO-04..05`
- `./gradlew.bat clean test` — 149 tests, 0 failures/errors
- `./gradlew.bat build` — BUILD SUCCESSFUL
- `verifyModuleBoundaries`
- `git diff --check`

## Engineering Finding

별도 Mock Supplier 설정이 필요한 E2E 클래스가 일반 테스트에 함께 포함돼 최종 게이트를 실패시켰고, 두 중첩 모듈이 같은 `application.jar` 이름을 만들어 Boot JAR 조립도 실패했습니다. 테스트 실행 책임과 archive 이름을 분리해 해결했습니다.

## Related Documentation

`docs/VERIFICATION.md`, `JOURNAL.md`, `AI_USAGE.md`를 실행 증거와 빌드 정상화 결과에 맞게 갱신했습니다.
