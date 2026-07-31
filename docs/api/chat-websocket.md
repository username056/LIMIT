# 채팅 WebSocket API

## 범위

- 채팅 WebSocket은 STOMP over WebSocket을 사용한다.
- REST 인증으로 발급받은 Access Token을 STOMP `CONNECT` 헤더에 전달한다.
- 메시지 전송, 방 이벤트 수신, 사용자별 ACK와 오류 수신, 읽음 처리를 지원한다.
- `TEXT`, `IMAGE`, `VIDEO` 메시지를 지원한다. 미디어는 업로드 API에서 먼저 등록한 뒤 반환된 `mediaId`를 전송한다.

## 연결

| Method | Endpoint | 설명 |
| --- | --- | --- |
| CONNECT | `/ws` | `Authorization: Bearer {accessToken}` 헤더로 회원 인증 |

`MEMBER` 계정만 연결할 수 있다. 인증 실패는 STOMP `ERROR` 프레임으로 반환된다.

## 구독

| Method | Destination | 설명 |
| --- | --- | --- |
| SUBSCRIBE | `/sub/chat-rooms/{roomId}` | 채팅방 이벤트 수신 |
| SUBSCRIBE | `/user/queue/chat-acks` | 내가 보낸 메시지 저장 ACK 수신 |
| SUBSCRIBE | `/user/queue/errors` | 사용자별 WebSocket 오류 수신 |

`/sub/chat-rooms/{roomId}` 구독은 채팅방 참여자만 허용한다. 권한이 없으면 구독을 취소하고 `/user/queue/errors`로 오류를 보낸다.

## 메시지 전송

Client sends:

```json
{
  "clientMessageId": "8e5654b5-b608-43a0-8e0f-1d63e31d41f9",
  "type": "TEXT",
  "content": "안녕하세요",
  "mediaIds": []
}
```

## REST 전송

WebSocket 재연결 중에도 같은 저장·중복 방지 계약을 사용할 수 있다.

- `POST /api/v1/chat-rooms/{roomId}/messages`
- 최초 저장: `201 Created`
- 같은 `clientMessageId` 재요청: `200 OK`와 기존 메시지

## 채팅방 나가기

- `DELETE /api/v1/chat-rooms/{roomId}`는 현재 사용자의 채팅 목록에서 방을 제거하고 `204 No Content`를 반환한다.
- 상대 참여자의 메시지와 방은 유지된다. 같은 매물에서 다시 문의하면 기존 방에 재입장한다.
- 메시지가 없는 방은 채팅 목록 화면에서 표시하지 않는다.
- `unreadCount`는 마지막 읽음 위치 이후 상대방이 보낸 메시지만 계산한다.

## 이미지·영상

1. `POST /api/v1/chat-rooms/{roomId}/media`에 `multipart/form-data`의 `file`을 전송한다.
2. 반환된 `mediaId` 하나를 `IMAGE` 또는 `VIDEO` 메시지의 `mediaIds`에 담아 전송한다.
3. 참여자는 `GET /api/v1/chat-media/{mediaId}/content`로 파일을 조회한다.

허용 형식은 JPEG, PNG, WebP, GIF(최대 20MB), MP4, WebM, QuickTime(최대 100MB)다.
파일은 운영의 공통 S3 미디어 버킷에서 `chat/{roomId}/` prefix로 저장한다.
운영 Nginx의 `client_max_body_size`는 101MB이고, Servlet 업로드 제한은
`SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE=100MB`,
`SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE=101MB`를 기본값으로 사용한다.

| Method | Destination | Request | ACK |
| --- | --- | --- | --- |
| SEND | `/pub/chat-rooms/{roomId}/messages` | `ChatMessageSendRequest` | `/user/queue/chat-acks` |

Server broadcasts:

```json
{
  "type": "MESSAGE",
  "roomId": 10,
  "message": {
    "messageId": 501,
    "roomSequence": 8,
    "senderId": 20,
    "clientMessageId": "8e5654b5-b608-43a0-8e0f-1d63e31d41f9",
    "type": "TEXT",
    "content": "안녕하세요",
    "status": "SENT",
    "sentAt": "2026-07-27T22:30:00"
  },
  "readerId": null,
  "lastReadSeq": null
}
```

중복 `clientMessageId`가 들어오면 기존 메시지를 ACK로 다시 반환하고 방 이벤트는 재발행하지 않는다.

## 읽음 처리

Client sends:

```json
{
  "lastReadSeq": 8
}
```

| Method | Destination | Request | Broadcast |
| --- | --- | --- | --- |
| SEND | `/pub/chat-rooms/{roomId}/read` | `ChatReadRequest` | `/sub/chat-rooms/{roomId}` |

Server broadcasts:

```json
{
  "type": "READ",
  "roomId": 10,
  "message": null,
  "readerId": 20,
  "lastReadSeq": 8
}
```

`lastReadSeq`가 방의 마지막 메시지 순서보다 크면 서버가 마지막 메시지 순서로 보정한다.

## 구현 메모

### 도메인 알림 이벤트

- 통화 약속 생성·수락·거절·변경·취소가 커밋되면 방 구독 채널로
  `{ "type": "CALL_APPOINTMENT_UPDATED", "roomId": 10 }`을 발행한다.
  클라이언트는 이벤트 수신 즉시 통화 약속 목록을 다시 조회한다.
- 재검수 알림은 `REINSPECTION_REQUESTED` 또는 `REINSPECTION_COMPLETED` 타입으로 발행하며,
  `message`와 구조화된 `reinspection` 데이터를 함께 제공한다. `reinspection`에는
  `listingId`, `requestKey`, 요청 체크리스트 항목이 포함되며 클라이언트는 이를 접을 수 있는
  SYSTEM 알림 카드로 표시한다. 과거 메시지 조회에서도 같은 구조화 데이터를 복원한다.
- 재검수 요청 시 같은 구매자·판매자 조합의 기존 채팅방이 있으면 매물이 달라도 최신 기존 방을
  재사용한다. 기존 방이 없을 때만 재검수용 채팅방을 생성한다.

- 방별 메시지 순서는 `chat_room` 행을 pessimistic lock으로 조회한 뒤 `last_message_seq + 1`로 발급한다.
- `chat_message`에는 `(chat_room_id, room_sequence)`와 `(chat_room_id, client_message_id)` unique constraint가 있어 순서 중복과 클라이언트 재전송 중복을 막는다.
- 같은 `clientMessageId` 동시 전송을 고려해 방 락 획득 후에도 중복 메시지를 한 번 더 확인한다.
