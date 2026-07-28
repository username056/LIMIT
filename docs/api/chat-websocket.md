# 채팅 WebSocket API

## 범위

- 채팅 WebSocket은 STOMP over WebSocket을 사용한다.
- REST 인증으로 발급받은 Access Token을 STOMP `CONNECT` 헤더에 전달한다.
- 메시지 전송, 방 이벤트 수신, 사용자별 ACK와 오류 수신, 읽음 처리를 지원한다.
- 현재 구현 범위는 `TEXT` 메시지다. `IMAGE`, `VIDEO`의 `mediaIds` 저장과 검증은 후속 작업이다.

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

- 방별 메시지 순서는 `chat_room` 행을 pessimistic lock으로 조회한 뒤 `last_message_seq + 1`로 발급한다.
- `chat_message`에는 `(chat_room_id, room_sequence)`와 `(chat_room_id, client_message_id)` unique constraint가 있어 순서 중복과 클라이언트 재전송 중복을 막는다.
- 같은 `clientMessageId` 동시 전송을 고려해 방 락 획득 후에도 중복 메시지를 한 번 더 확인한다.
