# 관리자 서비스

관리자 인증은 일반 회원과 분리한다. 일반 회원 권한은 `MEMBER`로 고정하고 관리자는 별도 `admin_account`에서 `OPERATOR` 또는 `SUPER_ADMIN` 권한을 가진다.

## API 범위

| Method | Path | 권한 | 설명 |
| --- | --- | --- | --- |
| `POST` | `/api/v1/admin/sessions` | 공개 | 관리자 로그인 |
| `GET` | `/api/v1/admin/members` | 관리자 | 회원 목록 |
| `GET` | `/api/v1/admin/members/{memberId}` | 관리자 | 회원 상세 |
| `GET` | `/api/v1/admin/members/{memberId}/restrictions` | 관리자 | 이용 제한 목록 |
| `POST` | `/api/v1/admin/members/{memberId}/restrictions` | 관리자 | 이용 제한 등록 |
| `PATCH` | `/api/v1/admin/member-restrictions/{restrictionId}` | 관리자 | 이용 제한 해제 |
| `GET` | `/api/v1/admin/action-logs` | 관리자 | 작업 로그 목록 |
| `GET` | `/api/v1/admin/action-logs/{actionLogId}` | 관리자 | 작업 로그 상세 |
| `GET` | `/api/v1/admin/accounts` | `SUPER_ADMIN` | 관리자 계정 목록 |
| `POST` | `/api/v1/admin/accounts` | `SUPER_ADMIN` | 관리자 계정 생성 |
| `PATCH` | `/api/v1/admin/accounts/{adminId}` | `SUPER_ADMIN` | 관리자 권한·상태 변경 |

마지막 활성 `SUPER_ADMIN`을 강등하거나 정지하는 요청은 거절한다.

## Swagger 인증

1. `POST /api/v1/admin/sessions`를 실행한다.
2. 응답의 `data.accessToken`을 복사한다.
3. Swagger UI의 `Authorize`를 누르고 토큰 값만 입력한다. UI가 `Bearer` 접두어를 붙인다.
4. 보호 API에 `Authorization: Bearer {accessToken}`이 전송되는지 확인한다.

헤더가 없거나 토큰이 만료되면 401, 인증은 됐지만 권한이 부족하면 403이다. `/api/v1/admin/accounts` 계열은 `SUPER_ADMIN`만 사용할 수 있다.

Refresh Token은 응답 JSON에 노출하지 않고 HttpOnly 쿠키로 발급한다. 재발급과 로그아웃은 회원과 동일하게 `/api/v1/auth/token-refreshes`, `/api/v1/auth/session-revocations`를 사용한다.

## 초기 최고 관리자

```env
INITIAL_ADMIN_ENABLED=true
INITIAL_ADMIN_EMAIL=
INITIAL_ADMIN_PASSWORD=
INITIAL_ADMIN_NAME=
INITIAL_ADMIN_ROLE=SUPER_ADMIN
```

계정 생성을 확인한 즉시 `INITIAL_ADMIN_ENABLED=false`로 되돌린다. 초기 비밀번호는 별도 API가 준비되는 대로 변경해야 하며 실제 값은 저장소에 남기지 않는다.
