# 재검수 요청/완료 API

## 구현 범위

- 구매자가 매물의 거래 채팅방을 통해 재검수(재촬영)를 요청하고, 판매자가 완료 처리한다.
- 요청자(buyerId)가 매물 판매자 본인이면 거절한다.
- 요청 시점에 매물-구매자-판매자 조합의 거래 채팅방이 없으면 채팅 도메인이 새로 만들고, 있으면
  기존 방을 그대로 쓴다(`ChatRoomService.getOrCreateChatRoom`). 구매자가 먼저 채팅방을 만들어 둘
  필요가 없다.
- 선택한 체크리스트 항목이 해당 매물 소속인지 검증한 뒤 `REQUESTED` 상태로 생성한다.
- 완료는 요청의 판매자 본인만 호출할 수 있고, `REQUESTED` 상태의 요청만 완료할 수 있다.
- 요청/완료 성공 시 채팅 도메인이 거래 채팅방에 SYSTEM 메시지를 저장하고 WebSocket으로 실시간
  전달한다(`ReinspectionNotificationEventListener`).

코드 위치: 컨트롤러·서비스·리포지토리·DTO·이벤트 모두 `domain.inspection` 아래 평평하게 있다
(`controller`, `service`, `repository`, `dto/{request,response}`, `event`) — 검수 도메인의 기존
OCR·DxDiag·배터리 리포트 코드와 같은 계층 구조를 따른다.

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

`reason`은 1000자 이하 필수, `items`는 1개 이상 100개 이하 필수이며 각 `requestContent`는 1000자
이하 필수다. 완료 API는 요청 본문이 없다.

응답(`ReinspectionRequestResponse`, 두 API 공통)은 생성 시점에 서버가 발급하는 `requestKey`(UUID)를
포함하며, 완료 API는 이 `requestKey`로 요청을 식별한다.

```json
{
  "data": {
    "requestKey": "b3b1c7a2-3c1a-4b1a-9c1a-1a2b3c4d5e6f",
    "status": "REQUESTED",
    "reason": "제품 상태를 조금 더 자세히 확인하고 싶습니다.",
    "items": [
      {
        "checklistItemId": 301,
        "itemName": "제품 외관",
        "requestContent": "모서리 흠집이 보이도록 가까이 촬영해 주세요.",
        "displayOrder": 0
      }
    ],
    "requestedAt": "2026-07-22T14:30:00",
    "completedAt": null
  },
  "meta": null
}
```

## 오류 코드

| 코드 | HTTP | 상황 |
| --- | --- | --- |
| `INS016` REINSPECTION_REQUEST_NOT_FOUND | 404 | `requestKey`에 해당하는 요청이 없음 |
| `INS017` REINSPECTION_ITEM_NOT_FOUND | 400 | 선택한 체크리스트 항목이 해당 매물 소속이 아님 |
| `INS018` REINSPECTION_BUYER_REQUIRED | 403 | 요청자(buyerId)가 매물 판매자 본인임 |
| `INS019` REINSPECTION_SELLER_REQUIRED | 403 | 완료 요청자가 해당 요청의 판매자가 아님 |
| `INS020` REINSPECTION_INVALID_STATE | 409 | `REQUESTED`가 아닌 요청을 다시 완료 처리하려 함 |
| `INS021` REINSPECTION_CHAT_ROOM_NOT_FOUND | 404 | 재검수 알림을 보낼 채팅방을 찾지 못함 |
| `PRD014` PRODUCT_NOT_FOUND | 404 | `listingId`에 해당하는 매물이 없음 |
| `CHT002` SELF_CHAT_NOT_ALLOWED | 400 | `getOrCreateChatRoom` 내부에서 buyer==seller 재확인 시 |

`INS021`은 두 API 응답으로는 나가지 않는다 — `ReinspectionChatNotificationService`가 커밋 이후
이벤트 리스너 안에서만 던지는 예외라서, `POST .../reinspection-requests`나 `.../complete` 호출자는
이 코드를 절대 못 본다(그래서 `ReinspectionRequestApi`의 `@ApiResponses`에도 올리지 않았다). 404로
맞춘 건 다른 `*_NOT_FOUND` 코드와의 일관성 때문이지, 클라이언트가 분기할 값이라서가 아니다.

## 도메인 이벤트 (채팅 도메인 연동)

요청·완료가 성공하면 같은 트랜잭션 커밋 후 Spring `ApplicationEventPublisher`로
`com.c203.limit.domain.inspection.event.ReinspectionNotificationEvent`를 발행한다.
Kafka/RabbitMQ 등 외부 브로커는 쓰지 않고, 채팅 도메인의
`ReinspectionNotificationEventListener`가 같은 JVM 안에서
`@TransactionalEventListener(phase = AFTER_COMMIT)`로 구독한다.

이벤트는 `type`(`REQUESTED`/`COMPLETED`)으로 방향을 명시한다. 요청 때는
`actorId=buyerId`/`recipientId=sellerId`, 완료 때는 반대다.

```json
{
  "eventId": "uuid",
  "type": "REQUESTED",
  "reinspectionRequestId": 1,
  "requestKey": "uuid",
  "chatRoomId": 25,
  "actorId": 100,
  "recipientId": 200,
  "pendingRequestCount": 1,
  "reason": "제품 상태를 조금 더 자세히 확인하고 싶습니다.",
  "items": [{ "name": "제품 외관", "requestContent": "..." }]
}
```

`pendingRequestCount`는 발행 시점 기준 같은 `chatRoomId`에서 `REQUESTED` 상태인 재검수 요청 개수다.

리스너 쪽 처리(`ReinspectionChatNotificationService` + `ReinspectionNotificationEventListener`):

1. `ChatMessage.clientMessageId`를 `event.eventId()`로 써서 `(chatRoomId, clientMessageId)`로
   기존 메시지가 있는지 먼저 조회한다. 있으면 그대로 반환하고 아래 2~3단계는 건너뛴다(중복 처리
   방지의 1차 방어선).
2. 없으면 새 트랜잭션(`REQUIRES_NEW`)에서 `chat_message`(SYSTEM)·`reinspection_request_message`·
   `chat_outbox_event`를 한 번에 저장한다. `reinspection_request_message`는 PK가
   `(reinspection_request_id, event_type)`이고 `chat_message_id`에 유니크 제약이 있어
   (`V20260803__create_reinspection_request_message.sql`), 1번 조회가 경합으로 놓친 동시 중복
   삽입 시도는 여기서 DB 유니크 제약 위반으로 걸러진다(2차 방어선).
3. 저장이 끝나면 `/sub/chat-rooms/{chatRoomId}`로 WebSocket 알림을 보내고
   `chat_outbox_event`를 발행 완료로 표시한다.

**배포 토폴로지·장애 시나리오에서의 한계 (알려진 갭, 아직 해결 안 됨)**

- **다중 인스턴스 배포**: `ApplicationEventPublisher`/`@TransactionalEventListener`는 인프로세스라
  이벤트 발행·소비가 같은 JVM(같은 요청을 받은 인스턴스) 안에서 끝난다. 여러 인스턴스로 스케일 아웃해도
  이벤트 자체가 인스턴스 간에 중복 발행될 일은 없다. 다만 WebSocket 브로드캐스트는
  `ChatWebSocketConfig`의 Spring 인메모리 `SimpleBroker`를 그대로 쓰기 때문에, 알림을 받아야 할
  클라이언트가 이벤트를 처리한 인스턴스가 아닌 **다른 인스턴스**에 WebSocket으로 연결돼 있으면 실시간
  전달을 못 받는다. 이건 재검수 기능만의 문제가 아니라 채팅 전체가 이미 갖고 있던 제약이고, 다중
  인스턴스에서 풀려면 외부 STOMP 릴레이 브로커(RabbitMQ 등)로 교체해야 한다.
- **리스너 처리 중 DB 실패**: `saveNew()`가 예외를 던지면(DB 커넥션 끊김 등) `@TransactionalEventListener`는
  자동 재시도를 하지 않는다. 예외는 로그로만 남고 이벤트는 유실된다 — 즉 재검수 요청/완료 API 자체는
  이미 200/201로 성공 응답을 준 뒤라서, 알림 저장만 조용히 실패할 수 있다. 현재는 재시도·데드레터
  큐가 없다. `chat_outbox_event`가 이런 실패를 감지·재발행하는 용도로 설계된 테이블이지만, 그 테이블을
  주기적으로 훑어 미발행 건을 재시도하는 배치/워커는 아직 없다.

## 데이터

`reinspection_request`, `reinspection_request_item` 테이블은
`V20260723__create_product_checklist_tables.sql`에 정의되어 있다. `reinspection_request_message`는
`V20260803__create_reinspection_request_message.sql`에서 추가됐고, `chat_message`를 FK로 참조한다.

`ReinspectionRequest.complete()`는 엔티티 레벨에서도 상태를 검증한다 — `REQUESTED`가 아니면
`REINSPECTION_INVALID_STATE`를 던진다(서비스 레벨 검증과 이중 방어).

## 검증

- `./gradlew test` 전체 스위트 통과, `./gradlew bootJar` 통과
- `docs/api/openapi.json` 재생성 완료
