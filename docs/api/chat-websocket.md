# 채팅 WebSocket 계약

## CHAT-BE-02 연결 인증

- Handshake endpoint: `/ws`
- Protocol: WebSocket + STOMP
- CONNECT header: `Authorization: Bearer {accessToken}`
- 허용 계정: `MEMBER`
- 인증 실패: STOMP `ERROR` frame의 `WebSocketErrorResponse`

HTTP handshake는 `/ws`까지 허용하고 실제 사용자 인증은 STOMP `CONNECT` frame에서 수행한다.

## 구독 권한

- 채팅방 이벤트: `/sub/chat-rooms/{roomId}`
- 개인 오류: `/user/queue/errors`

채팅방 이벤트는 탈퇴하지 않은 참여자만 구독할 수 있다. 비참여자 또는 허용되지 않은
목적지의 구독 요청은 브로커에 등록하지 않고 개인 오류 채널로
`CHAT_ROOM_ACCESS_DENIED(CHT004)`를 전달한다.

## 오류 응답

```json
{
  "error": {
    "code": "CHT004",
    "message": "채팅방에 접근할 권한이 없습니다.",
    "fieldErrors": []
  },
  "traceId": "generated-uuid"
}
```

연결 전 인증 오류는 STOMP `ERROR` frame으로 전달한다. 연결된 사용자의 구독 권한
오류는 연결을 유지한 채 `/user/queue/errors`로 전달한다.

## 아직 포함하지 않는 기능

- `CHAT-BE-03` 메시지 전송·저장·방송
- `CHAT-BE-12` Heartbeat 및 Redis TTL 연결 상태 관리
- `CHAT-BE-15` `ChatEventResponse` 발행

## 구현 중 트러블슈팅

### STOMP 인증이 HTTP JWT 필터를 통과하지 않는 문제

WebSocket handshake 이후의 STOMP frame은 일반 HTTP 요청이 아니므로 기존 JWT 필터만으로
인증할 수 없다. `/ws` handshake는 Spring Security에서 허용하고, inbound channel의
`ChannelInterceptor`가 STOMP `CONNECT` header의 Access Token을 검증하도록 분리했다.

### 인증된 사용자가 다른 채팅방을 구독할 수 있는 문제

JWT는 사용자 신원만 보장하고 채팅방 참여 여부는 보장하지 않는다. `SUBSCRIBE` frame의
`/sub/chat-rooms/{roomId}`에서 방 ID를 추출한 뒤, 탈퇴하지 않은 참여자인지 Repository로
확인한다. 권한 없는 SUBSCRIBE frame은 `null`을 반환해 브로커 등록을 막는다.

### 개인 오류 채널과 STOMP ERROR frame의 역할이 겹친 문제

세션이 아직 없는 CONNECT 인증 오류는 STOMP `ERROR` frame으로 반환한다. 연결된 사용자의
구독 권한 오류는 연결을 종료하지 않고 `/user/queue/errors`로 전송한다.

### WebSocket 설정 Bean의 순환 의존성

초기 구조는 `WebSocketConfig → AuthInterceptor → ErrorPublisher →
SimpMessagingTemplate → WebSocketConfig` 순환 의존성을 만들었다. 순환 참조 허용 설정을
사용하지 않고 `ObjectProvider<SimpMessagingTemplate>`로 실제 오류 발행 시점까지 조회를
지연했다.

### 통합 테스트의 handshake 401

최소 테스트 애플리케이션에는 운영 `SecurityConfig`가 없어 Spring Security 기본 정책이
`/ws`를 차단했다. 테스트에서도 HTTP handshake만 허용하고 STOMP CONNECT 인증은 실제
인터셉터가 담당하도록 경계를 동일하게 구성했다.

### Spring 7 JSON 변환기 제거 예정 경고

제거 예정인 `MappingJackson2MessageConverter` 대신 Spring 7의
`JacksonJsonMessageConverter`를 STOMP 테스트 클라이언트에 사용했다.

### 오류 JSON fallback의 인코딩과 traceId 누락

ObjectMapper 직렬화 실패 시 사용하던 fallback 문자열의 한글이 깨져 있었고 `traceId`도
없었다. ASCII 기반의 안전한 메시지와 생성된 `traceId`를 포함하는 JSON으로 보완했다.
