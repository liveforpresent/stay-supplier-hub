## Summary

최종 engineering record를 실제 구현·테스트·Git history에 맞춰 정리했습니다.

## Why

제출자가 시스템의 runtime 흐름, 핵심 판단, 검증 증거와 Mock Supplier fixture 범위를 빠르게 확인할 수 있어야 합니다.

## Changes

- README에 실제 build/run 방법, runtime flow, 아키텍처 판단, API·E2E 진입점을 추가했습니다.
- Mock Supplier의 availability·Catalog E2E fixture 범위를 Architecture와 Verification에 일치시켰습니다.
- 문서 수동 검토 증거를 반영해 `V-DOC-01..12`를 갱신했습니다.
- AI-028에 Context, Disposition, Human Judgment, Verification을 명시했습니다.

## Key Decisions

Mock Supplier의 추가 Catalog 모드는 운영 기능이 아니라 bootstrap baseline·batching E2E를 위한 fixture로만 문서화합니다.

## Verification

- `V-DOC-01..12` — MANUAL-PASS
- README 링크 대상 존재 확인
- `git diff --check`

## Engineering Finding

기존 Architecture의 Mock Supplier 모드 목록은 A Catalog 오류·stale baseline fixture를 빠뜨려 Verification의 `V-MCK-11..12`와 모순됐습니다. fixture 범위를 명시해 모순을 제거했습니다.

## Related Documentation

`README.md`, `docs/ARCHITECTURE.md`, `docs/VERIFICATION.md`, `AI_USAGE.md`를 갱신했습니다.
