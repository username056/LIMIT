# 프론트엔드 Sentry 오류·로그 모니터링

프론트엔드에서 발생한 처리되지 않은 JavaScript 오류와 Vue 오류, 공통 API
클라이언트의 네트워크 오류 및 5xx 응답은 Sentry에서 확인한다. 예상 가능한
4xx 응답은 수집하지 않는다. Nginx 접근 로그는 기존 Grafana Loki에서 계속
확인하며, 두 로그의 역할은 서로 다르다.

브라우저의 `console.log`, `console.info`, `console.warn`, `console.error`는
Sentry Logs로 전송한다. 운영 로그 양과 민감정보 노출을 줄이기 위해
`console.debug`, `console.trace`는 전송하지 않는다.

## GitLab CI/CD 변수

Sentry에서 Vue 프로젝트를 만든 뒤 다음 변수를 GitLab 프로젝트의 CI/CD
Variables에 등록한다.

| 변수 | 용도 | 권장 보호 |
| --- | --- | --- |
| `VITE_SENTRY_DSN` | 브라우저 SDK가 오류를 전송할 공개 DSN | protected |
| `VITE_SENTRY_ENVIRONMENT` | `production` 같은 환경 이름 | protected |
| `SENTRY_ORG` | source map 업로드 대상 organization slug | protected |
| `SENTRY_PROJECT` | source map 업로드 대상 project slug | protected |
| `SENTRY_AUTH_TOKEN` | source map 업로드용 인증 토큰 | masked, hidden, protected |

`VITE_*` 값은 빌드 결과에 포함되는 공개 설정이므로 Secret을 넣지 않는다.
`SENTRY_AUTH_TOKEN`은 브라우저 번들에 포함되지 않으며 저장소나 job 로그에
출력하지 않는다.

`dev` 또는 `main` 빌드에서는 Git commit SHA가 `VITE_SENTRY_RELEASE`로 자동
주입된다. source map 관련 세 변수가 모두 있으면 빌드 시 source map을 Sentry에
업로드하고 `frontend/dist`에서는 `.map` 파일을 삭제한다. 인증 정보가 없으면
일반 빌드는 성공하지만 source map 업로드는 건너뛴다.

## 확인 방법

1. 변경 사항을 `dev`에 병합하고 `frontend_lint`, `frontend_test`,
   `frontend_build`, `frontend_deploy_prod`, `frontend_smoke_prod`가
   성공했는지 확인한다.
2. Sentry의 **Issues**에서 Environment를 `production`으로 선택한다.
3. 신규 오류에 release commit SHA와 원본 Vue/JavaScript 파일·행 번호가
   표시되는지 확인한다.
4. 브라우저 요청 URL의 query/hash, 요청 cookie가 이벤트에서 제거됐는지
   확인한다.

시간순 프론트엔드 로그는 Sentry의 **Explore > Logs**에서 프로젝트와
`production` 환경을 선택해 확인한다. 로그 메시지와 속성의 인증정보,
cookie, 이메일, 전화번호 및 URL query/hash는 전송 전에 제거한다. 정제에
실패한 로그는 원본을 보내지 않고 폐기한다.

API 오류 context에도 method, query/hash를 제거한 path, status, 오류 code,
백엔드 traceId만 기록한다. 검색어·필터처럼 정상적인 query parameter라도
개인정보 포함 가능성을 우선해 수집하지 않는다. 요청 본문과 인증 헤더도
Sentry context에 전달하지 않는다.

DSN이 비어 있으면 Sentry는 초기화되지 않는다. Nginx 요청 로그는 Grafana
Explore에서 `{service="nginx"}`로 계속 조회한다.
