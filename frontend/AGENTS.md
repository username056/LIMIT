# 프론트엔드 작업 규칙

## 꼭 지키는 것 (보안이라 꼭 지키기!)
- 인증 토큰과 개인정보를 로그, localStorage, 코드에 임의 저장하지 않는다.
- API 주소는 환경변수와 공통 client에서 관리하고, 컴포넌트에 운영 주소를 하드코딩하지 않는다.
- API 호출은 `src/api/` 폴더를 거친다. (컴포넌트에서 axios 직접 호출 지양)
- 로딩, 성공, 빈 결과, 오류 상태를 화면에 명시한다.

## 최대한 지키려고 노력하는 것!
- Vue 3 Composition API와 `<script setup>`을 사용한다.
- API 응답의 `data`와 오류 응답 계약(`error.code`, `traceId`)을 확인한다.
- 주문(OrderDetailResponse), 대기열(QueueStatusResponse) 응답은 JSDoc `@typedef`로 정의한다.
- 새 컴포넌트를 만들 땐 렌더링 테스트 혹은 계속 화면 확인하면서 오류 있는지 확인하기

## 실행 명령
- 기능 변경 후 `npm run lint`, `npm run build`는 실행한다.
- `npm run test`는 작성한 테스트가 있을 때만 실행한다 (MVP 기간 중엔 강제 아님).

## 배포 파이프라인
- `dev` 또는 `main` 푸시에 `frontend/**/*` 등 프론트 변경 경로가 포함될 때만 프론트 lint, test, build, 배포 작업이 생성된다.
- 선행 작업 실패 후 백엔드 파일만 수정해 다시 푸시하면 프론트 배포는 재실행되지 않으므로, 파이프라인에서 `frontend_deploy_prod` 작업 생성 여부를 확인한다.
