# 회원·인증 서비스

## 구현 범위

- 이메일·닉네임 중복 확인 및 이메일 회원가입
- BCrypt 비밀번호 저장과 이메일 로그인
- HMAC-SHA256 JWT Access/Refresh Token 발급 및 Refresh Token Rotation
- 회원 프로필 조회·수정과 비밀번호 변경
- 소셜 로그인 공급자 포트와 소셜 계정 조회·해제

## 인증 설정

운영 환경에서는 `limit.security.jwt.secret`에 충분히 긴 비밀값을 외부 Secret으로 주입해야 한다.
Refresh Token 저장소는 기본값이 `memory`이므로 다중 인스턴스 또는 운영 환경에서는
`limit.security.refresh-store=redis`로 설정한다. 실제 비밀값과 토큰은 저장소 및 로그에 기록하지 않는다.

소셜 로그인은 `SocialIdentityClient`를 공급자별로 구현해 등록한다. 공급자 구현이 없는 경우
해당 로그인 요청은 `AUTH006`으로 거절된다.

## DB 반영 필요 사항

API 계약을 만족하려면 기존 ERD의 `user_account`에 `email_verified_at`, `last_login_at`,
`password_changed_at` 컬럼이 필요하다. `social_account`에는 응답용 이메일을 보존하기 위한
`provider_email` 컬럼과 `(provider, provider_user_id)` 유니크 제약이 필요하다.

운영 스키마 변경은 별도 migration PR에서 수행한다.
