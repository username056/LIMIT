# 프로젝트 작업 규칙

## 작업 순서

1. 이 파일과 작업 대상 폴더의 `AGENTS.md`를 먼저 읽는다.
2. 요구사항, 관련 코드, 인접 테스트만 우선 확인한다.
3. API·아키텍처 근거가 필요할 때만 `docs/`와 Notion 원문을 조회한다.
4. 허용된 범위에서 최소 변경을 구현한다.
5. 변경한 영역의 lint, test, build를 실행하고 결과와 미검증 항목을 보고한다.

## 수정 범위와 승인

- 일반 코드, 테스트, 개발 문서는 작업 요청 범위에서 수정할 수 있다.
- 운영 설정, 배포 실행, DB migration, CI credential, IAM/RBAC 변경은 사전 승인이 필요하다.
- 실제 `.env`, Secret, 토큰, 키, 인증서, 개인정보를 읽거나 출력하거나 커밋하지 않는다.
- force push, `git reset --hard`, 광범위 삭제, 운영 DB 변경을 실행하지 않는다.
- 문서와 코드 계약이 다르면 임의로 선택하지 말고 불일치로 보고한다.

## 공통 계약

- API prefix는 `/api/v1`을 사용한다.
- 성공 응답은 `{ "data": ..., "meta": null }` 형태를 사용한다.
- API 또는 설정 계약을 바꾸면 테스트와 `docs/`를 함께 갱신한다.
- 생성물, IDE 개인 설정, 실제 환경변수 파일은 커밋하지 않는다.

## 커밋 컨벤션

- 커밋 제목은 `<gitmoji> <type>: <명령형 요약>` 형식을 사용한다.
- 허용 type은 `feat`, `fix`, `refactor`, `test`, `docs`, `infra`, `chore`, `perf`, `security`다.
- Gitmoji와 type의 의미를 일치시키고 하나의 커밋에는 하나의 의도만 담는다.
- 예: `✨ feat: 주문 생성 API 구현`, `🐛 fix: readiness 실패 시 503 반환`, `📝 docs: 실행 가이드 보완`

## 기본 검증

```powershell
cd frontend
npm run lint
npm run test
npm run build
```

```powershell
cd backend
gradle test
gradle bootJar
```

완료 보고에는 변경 파일과 이유, 실행한 검증과 결과, 남은 위험과 확인하지 못한 항목을 포함한다.
