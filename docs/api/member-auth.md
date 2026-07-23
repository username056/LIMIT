# 회원·인증 서비스

## 구현 범위

- 이메일 회원가입, 이메일·닉네임 중복 확인
- 이메일 인증 메일 발송과 Redis 1회용 토큰 검증
- 이메일 로그인과 Google·Kakao·Naver 소셜 로그인
- 최초 소셜 로그인 가입 세션과 회원가입 완료
- 로그인한 회원의 소셜 계정 명시적 연결·조회·해제
- Access Token 발급과 HttpOnly Refresh Token 쿠키 회전
- 서비스·개인정보·만 14세 이상 필수 동의 및 마케팅 선택 동의 이력 저장

## 회원가입 계약

이메일 회원가입은 `POST /api/v1/members`를 사용한다. `email`, `password`, `nickname`은 필수이고 `phone`은 선택이다.

```json
{
  "email": "user@example.com",
  "password": "Password123",
  "nickname": "openrunner",
  "phone": null,
  "serviceTermsAccepted": true,
  "privacyTermsAccepted": true,
  "ageRequirementAccepted": true,
  "marketingAccepted": false
}
```

서비스 이용약관, 개인정보 처리방침, 만 14세 이상 동의 중 하나라도 `false`이면 `AUTH016`으로 거절한다. 동의 시점과 환경별 약관 버전은 `member_terms_agreement`에 저장한다.

## 이메일 인증 흐름

1. 회원가입 후 `POST /api/v1/auth/email-verification-requests`에 이메일을 보낸다.
2. 서버는 32바이트 난수 토큰을 발급하고 SHA-256 해시만 저장한다.
3. 메일 링크는 `FRONTEND_EMAIL_VERIFICATION_URL?token=...` 형식이다.
4. 프론트의 `/verify-email` 화면이 `POST /api/v1/auth/email-verifications`로 토큰을 보낸다.
5. 성공하면 `user_account.email_verified_at`을 기록한다. 토큰은 한 번 사용하면 삭제된다.
6. 이메일 인증 전 이메일 로그인은 `AUTH009`로 거절한다.

운영에서는 `EMAIL_VERIFICATION_STORE=redis`, `EMAIL_VERIFICATION_DELIVERY_ENABLED=true`와 SMTP 환경변수를 사용한다.

## 소셜 로그인과 최초 가입

1. 프론트가 `POST /api/v1/auth/social-authorizations/{provider}`로 인가 URL을 요청한다.
2. 서버는 OAuth `state`를 Redis에 저장하고 브라우저에도 HttpOnly 쿠키로 설정한다.
3. 공급자 콜백을 받은 프론트가 code와 state를 `POST /api/v1/auth/social-sessions/{provider}`로 전달한다.
4. 이미 연결된 계정이면 `AUTHENTICATED`와 Access Token을 반환한다.
5. 미가입 계정이면 회원을 만들지 않고 `SIGNUP_REQUIRED`와 표시용 이메일·추천 닉네임을 반환한다. 가입 세션은 HttpOnly 쿠키에 15분간 유지한다.
6. 프론트가 필수 정보와 약관 동의를 `POST /api/v1/auth/social-signups`로 보내면 회원과 소셜 연결을 한 트랜잭션에서 생성한다.

공급자 이메일과 같은 기존 회원이 있더라도 자동 연결하지 않는다. `AUTH014`를 반환하며, 기존 계정으로 로그인한 뒤 다음 API로 명시적으로 연결해야 한다.

- `POST /api/v1/members/me/social-authorizations/{provider}`
- `POST /api/v1/members/me/social-accounts/{provider}`
- `GET /api/v1/members/me/social-accounts`
- `DELETE /api/v1/members/me/social-accounts/{socialAccountId}`

## 토큰과 쿠키

Access Token은 성공 응답의 `data.accessToken`으로 반환하며 API 요청의 `Authorization: Bearer {accessToken}` 헤더에 사용한다. Refresh Token은 JSON에 포함하지 않고 `HttpOnly`, `SameSite=Lax` 쿠키로만 전달한다.

- 로컬: `AUTH_COOKIE_SECURE=false`
- HTTPS 운영: `AUTH_COOKIE_SECURE=true`
- 재발급: `POST /api/v1/auth/token-refreshes` (요청 본문 없음)
- 로그아웃: `POST /api/v1/auth/session-revocations` (요청 본문 없음)

프론트는 새로고침 시 Refresh Token 쿠키로 Access Token을 복구하며 인증 토큰을 localStorage나 sessionStorage에 저장하지 않는다.

## DB 반영

기존 DB에는 `backend/src/main/resources/db/manual/V20260722__member_terms_agreement.sql`을 검토 후 수동 적용한다. 전체 절차는 `docs/integration/database-schema-runbook.md`를 따른다.
