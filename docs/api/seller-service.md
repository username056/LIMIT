# 판매자 서비스

회원과 판매자는 서로 배타적인 회원 유형이 아니다. 모든 사용자는 `MEMBER`이며, 활성 판매자
프로필이 있으면 `SELLER` 역할을 추가로 가진다.

## 즉시 등록 흐름

1. 이메일 인증을 마친 활성 회원이 `POST /api/v1/sellers`를 호출한다.
2. 서버는 별도 신청서나 관리자 심사 없이 `ACTIVE` 판매자 프로필을 생성한다.
3. 프론트는 `POST /api/v1/auth/token-refreshes`를 호출해 새 Access Token을 받는다.
4. 새 JWT와 회원 요약에는 `MEMBER`, `SELLER` 역할 및 `sellerStatus: ACTIVE`가 반영된다.
5. 이후 판매자 상품 API와 `GET /api/v1/sellers/me`를 사용할 수 있다.

동일 회원의 중복 등록은 `409 SEL003`, 활성 판매자 프로필이 없는 상품 변경 요청은
`403 SEL010`으로 거절한다.

## 등록 API

```http
POST /api/v1/sellers
Authorization: Bearer {memberAccessToken}
Content-Type: application/json
```

```json
{
  "sellerType": "INDIVIDUAL",
  "countryCode": "KR",
  "businessName": null,
  "settlementBankName": "국민은행",
  "settlementAccountHolder": "판매자",
  "settlementAccountLast4": "1234",
  "sellerTermsAccepted": true
}
```

사업자 판매자는 `businessName`이 필수다. 계좌번호 전체는 저장하지 않고 확인용 마지막
4자리만 저장한다.

## 역할 저장 기준

권한의 기준 데이터는 다음과 같이 통일한다.

- `MEMBER`: 활성 회원 계정이면 항상 부여
- `SELLER`: `seller.user_id`로 연결된 프로필의 상태가 `ACTIVE`일 때만 부여
- `user_account.member_type`: 기존 데이터 이관 호환용이며 런타임 권한 판정에 사용하지 않음

로그인, 소셜 로그인, 토큰 갱신, 회원 정보 응답은 모두 같은 판매자 프로필 조회 결과를
사용한다. 이미 발급된 JWT에 `SELLER`가 남아 있더라도 상품 변경 직전에 DB의 활성 판매자
상태를 다시 확인한다.

## DB 마이그레이션

Flyway `V20260802__create_instant_seller_profiles.sql`이 판매자 프로필 컬럼과 회원별 유일
인덱스를 구성한다. 기존 `seller` 테이블과 `user_account.member_type = SELLER` 데이터는
삭제하지 않고 새 역할 기준으로 이관한다.
