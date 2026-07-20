---
name: pr-check
description: PR 또는 Merge Request를 만들기 전 변경 범위, Secret, API·문서 계약, lint, test, build, 배포·롤백 위험을 최종 점검한다.
---

# PR 점검

## 목적

변경이 안전하게 리뷰와 CI 단계로 넘어갈 수 있는지 근거를 확인한다.

## 점검 순서

1. `git status`와 diff로 변경 범위와 의도를 확인한다.
2. 실제 `.env`, 키, 토큰, 개인정보, 생성물이 포함되지 않았는지 확인한다.
3. API·DB·환경 설정 변경에 테스트와 문서가 동반됐는지 확인한다.
4. 프론트 변경 시 lint, test, build를 실행한다.
5. 백엔드 변경 시 test, bootJar, JaCoCo 결과를 확인한다.
6. CI, Docker, 배포 설정 변경 시 영향과 rollback 방법을 확인한다.
7. 실행하지 못한 검증과 남은 위험을 숨기지 않고 기록한다.

## 산출물

연관 이슈, 문제와 선택, 변경 내용, 검증 명령과 결과, 배포·롤백, 문서 반영, 위험과 후속 작업 순서로 PR 요약을 작성한다.

## 완료 기준

Critical·Major 문제와 Secret 노출이 없고 필수 검증이 통과해야 한다. 실패한 Quality Gate나 테스트를 우회하지 않는다.

