---
name: pr-check
description: PR 또는 Merge Request를 만들기 전 변경 범위, Secret, API·문서 계약, lint, test, build, 배포·롤백 위험을 최종 점검한다.
---

# PR 점검

## 목적

변경이 안전하게 리뷰와 CI 단계로 넘어갈 수 있는지 근거를 확인한다.

## 점검 순서

1. 현재 브랜치가 최신 `dev`에서 분기됐고 `<type>/<kebab-case>` 형식이며 하나의 목적만 갖는지 확인한다.
2. PR 대상이 `dev`이고 squash merge를 사용할 계획인지 확인한다.
3. `git status`와 diff로 변경 범위와 의도를 확인한다.
4. 실제 `.env`, 키, 토큰, 개인정보, 생성물이 포함되지 않았는지 확인한다.
5. API·DB·환경 설정 또는 구조 변경에 테스트와 OpenAPI·README·ADR·Runbook 문서가 동반됐는지 확인한다.
6. 프론트 변경 시 lint, test, build를 실행한다.
7. 백엔드 변경 시 test, bootJar, JaCoCo 결과를 확인한다.
8. secret scan, 정적 분석·커버리지, 의존성 취약점, API·이벤트 계약 검사 결과를 확인한다.
9. CI, Docker, 배포 설정 변경 시 영향과 rollback 방법을 확인한다.
10. 실행하지 못한 검증과 남은 위험을 숨기지 않고 기록한다.

## 산출물

PR 요약은 별도 이슈 번호를 요구하지 않는다. `## 📝 작업 내용` 아래에 이슈를 직접 열지 않아도 변경 사항을 이해할 수 있도록 핵심 작업을 한 줄씩 bullet로 작성한다. 내부 점검 결과는 요청받거나 실패·위험이 있을 때 추가한다. Squash 후 최종 커밋 제목도 컨벤션에 맞춘다.

## 완료 기준

Critical·Major 문제와 Secret 노출이 없고 필수 검증이 통과해야 한다. 실패한 Quality Gate나 테스트를 우회하지 않는다.
