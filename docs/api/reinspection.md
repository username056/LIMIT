# 재검수 요청/완료 API

## 구현 범위

- 구매자가 매물의 거래 채팅방을 통해 재검수(재촬영)를 요청하고, 판매자가 완료 처리한다.
- 요청자(buyerId)가 매물 판매자 본인이면 거절한다(자기 매물에 재검수 요청 불가).
- 요청 시점에 매물-구매자-판매자 조합의 거래 채팅방이 없으면 채팅 도메인이 새로 만들고, 있으면
  기존 방을 그대로 쓴다. 즉 구매자가 먼저 채팅방을 만들어 둘 필요가 없다.
- 선택한 체크리스트 항목이 해당 매물 소속인지 검증한 뒤 `REQUESTED` 상태로 생성한다.
- 완료는 요청의 판매자 본인만 호출할 수 있고, `REQUESTED` 상태의 요청만 완료할 수 있다.
- 채팅방에 실제 시스템 메시지를 저장·WebSocket으로 실시간 전달하는 것은 채팅 담당 범위이며 이번
  구현에서 제외했다. `reinspection_request_message` 테이블도 채팅 담당이 별도로 만든다.

**미완성 의존성 주의**: 위 "채팅방 없으면 생성" 부분은 `ChatRoomService.getOrCreateChatRoom(listingId,
buyerId, sellerId)`를 호출하는데, 이 메서드는 아직 실제 조회·생성 로직이 없는
스텁이다([ChatRoomService.java](../../backend/src/main/java/com/c203/limit/domain/chat/service/ChatRoomService.java)의
`TODO(채팅 담당)` 참고). 지금은 예외 없이 고정값 `-1`을 반환하므로 재검수 요청 API 자체는
201로 정상 응답하지만, 응답의 `chatRoomId`는 실제 채팅방을 가리키지 않는다. 채팅 담당이 이
메서드를 실제로 구현하면 진짜 `chatRoomId`로 바뀐다.

## API

| Method | Path | 설명 |
| --- | --- | --- |
| POST | `/api/v1/listings/{listingId}/reinspection-requests` | 재검수 요청 (구매자) |
| POST | `/api/v1/reinspection-requests/{requestKey}/complete` | 재검수 완료 (판매자) |

요청 DTO(`ReinspectionRequestCreateRequest`):

```json
{
  "reason": "제품 상태를 조금 더 자세히 확인하고 싶습니다.",
  "items": [
    { "checklistItemId": 301, "requestContent": "모서리 흠집이 보이도록 가까이 촬영해 주세요." }
  ]
}
```

`reason`은 1000자 이하 필수, `items`는 1개 이상 필수이며 각 `requestContent`는 1000자 이하
필수다. 완료 API는 요청 본문이 없다.

응답(`ReinspectionRequestResponse`, 두 API 공통)은 생성 시점에 서버가 발급하는 `requestKey`
(UUID)를 포함하며, 완료 API는 이 `requestKey`로 요청을 식별한다.

```json
{
  "data": {
    "id": 1,
    "requestKey": "b3b1c7a2-3c1a-4b1a-9c1a-1a2b3c4d5e6f",
    "listingId": 10,
    "chatRoomId": 25,
    "buyerId": 100,
    "sellerId": 200,
    "reason": "제품 상태를 조금 더 자세히 확인하고 싶습니다.",
    "status": "REQUESTED",
    "requestedAt": "2026-07-22T14:30:00",
    "completedAt": null,
    "items": [
      {
        "checklistItemId": 301,
        "itemName": "제품 외관",
        "requestContent": "모서리 흠집이 보이도록 가까이 촬영해 주세요.",
        "displayOrder": 1
      }
    ]
  },
  "meta": null
}
```

## 오류 코드

| 코드 | HTTP | 상황 |
| --- | --- | --- |
| `INS016` REINSPECTION_SELF_REQUEST_NOT_ALLOWED | 400 | 요청자(buyerId)가 매물 판매자 본인임 |
| `INS017` REINSPECTION_ITEM_LISTING_MISMATCH | 400 | 선택한 체크리스트 항목이 해당 매물 소속이 아님 |
| `INS018` REINSPECTION_REQUEST_NOT_FOUND | 404 | `requestKey`에 해당하는 요청이 없음 |
| `INS019` REINSPECTION_ACCESS_DENIED | 403 | 완료 요청자가 해당 요청의 판매자가 아님 |
| `INS020` REINSPECTION_ALREADY_PROCESSED | 409 | `REQUESTED`가 아닌 요청을 다시 완료 처리하려 함 |
| `PRD014` PRODUCT_NOT_FOUND | 404 | `listingId`에 해당하는 매물이 없음 |

`INS016`은 원래 "채팅방 참여자 검증 실패"(REINSPECTION_NOT_PARTICIPANT) 의미로 쓰다가, 채팅방을
자동 생성하는 방식으로 바뀌면서 그 검증이 사라져 코드를 회수했다. 이후 채팅 담당 요청으로
"본인 매물 요청 거절" 검증을 다시 추가하면서 같은 번호를 다른 의미로 재사용했다 — 아직 배포된 적
없는 코드라 `docs/api/README.md`의 "이미 배포된 코드 재사용 금지" 규칙에 걸리지 않는다.

## 도메인 이벤트 (채팅 담당 연동 계약)

요청·완료가 성공하면 같은 트랜잭션 커밋 후 Spring `ApplicationEventPublisher`로 인프로세스
이벤트를 발행한다. Kafka/RabbitMQ 등 외부 브로커는 쓰지 않으며, 채팅 도메인은 같은 JVM 안에서
`@TransactionalEventListener(phase = AFTER_COMMIT)` 리스너로 구독하는 것을 전제로 설계했다.

요청과 완료 두 시점 모두 **하나의 이벤트 타입**을 재사용한다:
`com.c203.limit.domain.inspection.reinspection.event.ReinspectionNotificationEvent`.
`actorId`(이번 행동을 한 사람)와 `recipientId`(알림 받을 사람)로 방향을 구분한다 — 요청 때는
`actorId=buyerId`/`recipientId=sellerId`, 완료 때는 `actorId=sellerId`/`recipientId=buyerId`가
된다. 이벤트 자체에는 "요청"과 "완료"를 구분하는 별도 타입 필드가 없으므로, 필요하다면 구독 쪽에서
`reinspectionRequestId`로 현재 상태(`status`)를 조회해서 판단해야 한다.

```json
{
  "reinspectionRequestId": 1,
  "requestKey": "uuid",
  "chatRoomId": 25,
  "actorId": 100,
  "recipientId": 200,
  "pendingRequestCount": 1,
  "reason": "제품 상태를 조금 더 자세히 확인하고 싶습니다.",
  "items": [
    { "checklistItemId": 301, "itemName": "제품 외관", "requestContent": "...", "displayOrder": 1 }
  ]
}
```

`pendingRequestCount`는 이벤트를 발행하는 시점 기준, 같은 `chatRoomId`에서 `REQUESTED` 상태인
재검수 요청 개수다(`ReinspectionRequestRepository.countByChatRoomIdAndStatus`). 요청 발행 시에는
방금 만든 요청을 포함한 값, 완료 발행 시에는 방금 완료 처리한 요청은 제외한 값이 된다.

이번 구현에는 리스너가 없다. 채팅 담당이 `ReinspectionNotificationEvent`를 구독해 `chat_message`
(SYSTEM), `reinspection_request_message`, `chat_outbox_event`를 저장하고 WebSocket으로 상대에게
전달하는 부분을 별도로 구현한다. 이 부분은 검수 쪽에서 중복 구현하지 않는다.

## 데이터

`reinspection_request`, `reinspection_request_item` 테이블은
`V20260723__create_product_checklist_tables.sql`에 이미 정의되어 있으며 이번 작업에서 스키마를
변경하지 않았다. `reinspection_request_item.listing_checklist_item_id`는 `listing_checklist_item`을
FK로 직접 참조하므로 두 엔티티는 `domain.inspection.entity`에 그대로 둔다. 신규 컨트롤러·서비스·
리포지토리·DTO·이벤트는 `domain.inspection.reinspection` 하위 패키지로 분리해 기존 검수(OCR·
DxDiag·배터리 리포트) 코드와 섞이지 않게 했다.

`chat_room` 확보는 더 이상 읽기 전용 리더가 아니라, chat 도메인의 `ChatRoomService`를 직접
주입받아 `getOrCreateChatRoom(listingId, buyerId, sellerId)`를 호출하는 방식으로 바꿨다(AGENTS.md가
허용하는 "도메인 간 Service 의존" 방식, 엔티티 직접 참조 아님). 기존 `ReinspectionChatRoomReader`
(JdbcClient 읽기 전용 리더)는 제거했다.

## 검증

- 백엔드 단위·컨트롤러 테스트: `ReinspectionRequestServiceTests` 8건, `ReinspectionRequestControllerTests`
  2건, 전체 스위트 329건 통과
- `./gradlew bootJar` 통과
- `docs/api/openapi.json` 재생성 완료 (`sh scripts/export-openapi.sh docs/api/openapi.json`)
- `getOrCreateChatRoom`은 스텁 상태(고정값 `-1` 반환)라 재검수 요청 API 자체는 정상 응답하지만
  `chatRoomId`가 실제 채팅방을 가리키지 않음 (서비스 테스트는 `ChatRoomService`를 모킹해서 검증)
