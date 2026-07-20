# 판매자 서비스

판매자 신청서는 `DRAFT`, `SUBMITTED`, `UNDER_REVIEW`, `APPROVED`, `REJECTED`, `CANCELED`
상태를 사용한다. 회원은 자신의 신청서만 조회·변경할 수 있으며 승인된 신청서는 판매자 프로필로 전환된다.

증빙 파일 저장은 `SellerDocumentStorage` 포트로 분리했다. S3 구현체는 파일 악성 코드 검사와
암호화, 전용 버킷 접근 정책을 적용한 뒤 별도 인프라 PR에서 연결한다.

## DB 반영 필요 사항

- `seller_application`: `application_version`, `seller_type`, `country_code`, `business_name`,
  `business_number`, `settlement_bank_name`, `settlement_account_holder`, `planned_category`,
  `submitted_at`, `reviewed_at`, `updated_at` 컬럼 추가
- `seller_application.status`: API 상태 6종을 지원하도록 ENUM 변경
- ERDCloud 임시 컬럼 `Field`, `Field2`: 각각 `settlement_account_holder`,
  `settlement_bank_name`으로 이름 변경
- `seller_application_document`: 신청서별 다중 증빙을 보존하기 위해 신규 테이블 추가
- `seller`: `seller_category`를 API 용어와 일치하는 `seller_type`으로, `country`를
  `country_code`로 변경하고 `business_name`을 추가

정산 계좌는 애플리케이션 계층 또는 DB 암호화 기능을 사용해 암호화 저장해야 한다.
