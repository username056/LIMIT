# 마켓플레이스 신고·운영 API

## 운영 상태 모델

상품의 판매 생명주기 `listing.status`와 신고 조치 `listing.moderation_status`는 서로 다른
축이다. 경고 확인 여부를 별도 상품 행이나 판매 상태에 섞지 않는다.

| 운영 상태 | 구매자 목록·상세 | 판매자 본인 | 관리자 | 의미 |
| --- | --- | --- | --- | --- |
| `NORMAL` | 공개 | 조회 | 조회 | 정상 |
| `WARNING_ACK_REQUIRED` | 공개 | 신고·경고 내역과 확인 버튼 표시 | 조회 | 판매는 유지하되 판매자 확인 필요 |
| `SUSPENDED` | 비공개 | 조회·수정·복구 신청 | 조회 | 관리자 판매 중지 |
| `RESTORE_REQUESTED` | 비공개 | 조회 | 조회·승인·반려 | 관리자 복구 심사 중 |

공개 상품 조건은 `status = ON_SALE AND moderation_status IN
('NORMAL', 'WARNING_ACK_REQUIRED')`이다. 이미지, 체크리스트, 증빙, 자동 진단 요약, 좋아요,
채팅과 구매도 같은 가시성 규칙을 적용한다. 공개 상세 응답은 내부 경고 상태와 신고 내역을
숨기고 `moderationStatus=NORMAL`로 응답한다. 판매자 본인과 관리자 전용 상세에만 실제 운영
상태와 `moderationNotices`를 제공하며 신고자 식별자는 판매자에게 노출하지 않는다.

## 사용자 API

| Method | Path | 권한 | 설명 |
| --- | --- | --- | --- |
| `POST` | `/api/v1/products/{productId}/reports` | 로그인 회원 | 공개 상품 신고 |
| `POST` | `/api/v1/products/{productId}/moderation-warning-acknowledgements` | 활성 판매자·상품 소유자 | 경고 확인 |
| `POST` | `/api/v1/products/{productId}/restoration-requests` | 활성 판매자·상품 소유자 | 수정 후 복구 신청 |

신고 요청은 `category`와 5~1000자의 `detail`을 받는다. 분류는 다음과 같다.

- `INACCURATE_INFORMATION`: 상품 정보가 사실과 다름
- `DUPLICATE_LISTING`: 중복 등록
- `FRAUD_SUSPECTED`: 사기 의심
- `PROHIBITED_ITEM`: 판매 금지 상품
- `INAPPROPRIATE_CONTENT`: 부적절한 내용
- `OTHER`: 기타

본인 상품 신고는 `SELF_REPORT_NOT_ALLOWED`, 같은 회원의 같은 상품 재신고는
`REPORT_ALREADY_EXISTS`로 거절한다. 판매 중지 후 복구 신청은 상품 정보를 먼저 수정할 수
있지만 신청만으로 공개되지 않는다.

## 관리자 API와 상태 전이

관리자 신고 결정은 `DISMISS`, `WARN`, `SUSPEND`다. `WARN`은 상품을
`WARNING_ACK_REQUIRED`로 바꾸고 판매자가 경고를 확인하면 `NORMAL`로 돌아간다.
`SUSPEND`는 즉시 구매자 노출과 새 구매·문의 경로를 차단한다.

복구 결정은 `APPROVE`, `REJECT`다. 승인하면 `RESTORE_REQUESTED -> NORMAL`, 반려하면
`RESTORE_REQUESTED -> SUSPENDED`로 전이하고 반려 사유를 판매자에게 보여 준다. 모든 관리자
결정은 `admin_action_log`에 기록한다. 관리자 엔드포인트 목록은
[관리자 서비스](admin-service.md)에 정리한다.

## 이상 활동 탐지

탐지는 자동 제재가 아니라 관리자 확인 우선순위를 만드는 `moderation_risk_signal`만 생성한다.

- 판매 시작 빈도: 최초 `DRAFT -> ON_SALE` 전이를 기준으로 최근 7일 30건 이상
- 유사 상품명: 같은 판매자의 같은 기기 모델 제목을 정규화한 뒤 bigram Jaccard 유사도 85% 이상
- 유사 이미지: 같은 판매자의 다른 상품 이미지가 SHA-256으로 같거나 64-bit dHash 해밍 거리 8 이하

기본값은 `MODERATION_PUBLISHING_THRESHOLD`, `MODERATION_PUBLISHING_WINDOW_DAYS`,
`MODERATION_TITLE_SIMILARITY_THRESHOLD`, `MODERATION_IMAGE_HAMMING_THRESHOLD` 환경변수로
조정한다. 이미지 분석은 업로드 완료 트랜잭션 커밋 후 실행하며 분석 실패가 상품 등록을
실패시키지 않는다.

## 데이터 변경과 배포

`V20260828__create_marketplace_moderation.sql`은 다음을 추가한다.

- `listing.moderation_status`와 공개·판매자 운영 조회 인덱스
- `listing_report`, `listing_restoration_request`, `moderation_risk_signal`
- `listing_image.content_sha256`, `perceptual_hash`, `analyzed_at`

기존 상품은 migration 기본값으로 모두 `NORMAL`이 된다. 운영 반영 전 백업 후 Flyway 적용과
Hibernate `ddl-auto=validate` 통과를 확인한다. 롤백이 필요하면 신규 운영 기능을 먼저 중지하고
기존 애플리케이션으로 되돌린 뒤, 신규 테이블·컬럼에 기록된 데이터를 보존한 채 별도 승인된
후속 migration으로 정리한다.
