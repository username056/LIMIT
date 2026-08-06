package com.c203.limit.domain.rtc.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@ExtendWith(MockitoExtension.class)
class RtcSignalingHandlerTests {
    private static final long ROOM_ID = 77L;
    private static final long SELLER_ID = 11L;
    private static final long BUYER_ID = 22L;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private RtcSignalingHandler handler;

    @BeforeEach
    void setUp() {
        handler = new RtcSignalingHandler(objectMapper);
    }

    @Test
    void acknowledgesJoinAndNotifiesExistingParticipant() throws Exception {
        WebSocketSession seller = participant("ws-1", ROOM_ID, SELLER_ID);
        WebSocketSession buyer = participant("ws-2", ROOM_ID, BUYER_ID);
        when(seller.isOpen()).thenReturn(true);

        handler.afterConnectionEstablished(seller);
        handler.afterConnectionEstablished(buyer);

        List<JsonNode> sellerMessages = sentMessages(seller, 2);
        assertThat(sellerMessages.get(0).path("type").asText()).isEqualTo("joined");
        assertThat(sellerMessages.get(0).path("participantCount").asInt()).isEqualTo(1);
        assertThat(sellerMessages.get(1).path("type").asText()).isEqualTo("peer-ready");

        List<JsonNode> buyerMessages = sentMessages(buyer, 1);
        assertThat(buyerMessages.get(0).path("type").asText()).isEqualTo("joined");
        assertThat(buyerMessages.get(0).path("participantCount").asInt()).isEqualTo(2);
    }

    @Test
    void closesPreviousOpenSessionWhenSameMemberReconnects() throws Exception {
        WebSocketSession previous = participant("ws-1", ROOM_ID, SELLER_ID);
        WebSocketSession reconnected = participant("ws-2", ROOM_ID, SELLER_ID);
        when(previous.isOpen()).thenReturn(true);

        handler.afterConnectionEstablished(previous);
        handler.afterConnectionEstablished(reconnected);

        verify(previous).close(CloseStatus.NORMAL);
        assertThat(sentMessages(reconnected, 1).get(0).path("participantCount").asInt())
                .isEqualTo(1);
    }

    @Test
    void doesNotCloseAlreadyClosedPreviousSession() throws Exception {
        WebSocketSession previous = participant("ws-1", ROOM_ID, SELLER_ID);
        WebSocketSession reconnected = participant("ws-2", ROOM_ID, SELLER_ID);
        when(previous.isOpen()).thenReturn(false);

        handler.afterConnectionEstablished(previous);
        handler.afterConnectionEstablished(reconnected);

        verify(previous, never()).close(CloseStatus.NORMAL);
    }

    @Test
    void closesConnectionWhenSignalPayloadExceedsSizeLimit() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);

        handler.handleTextMessage(session, new TextMessage("a".repeat(64 * 1024 + 1)));

        verify(session).close(new CloseStatus(1009, "signal message too big"));
        verify(session, never()).sendMessage(any(TextMessage.class));
    }

    @Test
    void rejectsUnsupportedSignalType() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("ws-1");

        handler.handleTextMessage(session, new TextMessage("{\"type\":\"screen-share\"}"));

        JsonNode sent = sentMessages(session, 1).get(0);
        assertThat(sent.path("type").asText()).isEqualTo("error");
        assertThat(sent.path("code").asText()).isEqualTo("UNSUPPORTED_SIGNAL");
    }

    @Test
    void rejectsSignalWithoutTypeField() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("ws-1");

        handler.handleTextMessage(session, new TextMessage("{\"payload\":{}}"));

        assertThat(sentMessages(session, 1).get(0).path("code").asText())
                .isEqualTo("UNSUPPORTED_SIGNAL");
    }

    @Test
    void failsFastOnMalformedJsonSignal() {
        WebSocketSession session = mock(WebSocketSession.class);

        assertThatThrownBy(() ->
                        handler.handleTextMessage(session, new TextMessage("{ not json")))
                .isInstanceOf(JsonProcessingException.class);
    }

    @Test
    void relaysOfferOnlyToTheOtherParticipant() throws Exception {
        WebSocketSession seller = participant("ws-1", ROOM_ID, SELLER_ID);
        WebSocketSession buyer = participant("ws-2", ROOM_ID, BUYER_ID);
        when(seller.isOpen()).thenReturn(true);
        when(buyer.isOpen()).thenReturn(true);

        handler.afterConnectionEstablished(seller);
        handler.afterConnectionEstablished(buyer);
        handler.handleTextMessage(
                seller, new TextMessage("{\"type\":\"offer\",\"payload\":{\"sdp\":\"v=0\"}}"));

        List<JsonNode> buyerMessages = sentMessages(buyer, 2);
        assertThat(buyerMessages.get(1).path("type").asText()).isEqualTo("offer");
        assertThat(buyerMessages.get(1).path("payload").path("sdp").asText()).isEqualTo("v=0");
        // 발신자는 joined + peer-ready 외에 자신의 시그널을 되돌려 받지 않는다.
        sentMessages(seller, 2);
    }

    @Test
    void skipsClosedPeerWhenRelayingSignal() throws Exception {
        WebSocketSession seller = participant("ws-1", ROOM_ID, SELLER_ID);
        WebSocketSession buyer = participant("ws-2", ROOM_ID, BUYER_ID);
        when(seller.isOpen()).thenReturn(true);
        when(buyer.isOpen()).thenReturn(false);

        handler.afterConnectionEstablished(seller);
        handler.afterConnectionEstablished(buyer);
        handler.handleTextMessage(
                seller,
                new TextMessage("{\"type\":\"ice-candidate\",\"payload\":{\"candidate\":\"a\"}}"));

        assertThat(sentMessages(buyer, 1).get(0).path("type").asText()).isEqualTo("joined");
    }

    @Test
    void ignoresSignalWhenRoomIsNotRegistered() throws Exception {
        WebSocketSession session = participant("ws-1", ROOM_ID, SELLER_ID);

        handler.handleTextMessage(
                session, new TextMessage("{\"type\":\"hangup\",\"payload\":{}}"));

        verify(session, never()).sendMessage(any(TextMessage.class));
    }

    @Test
    void notifiesRemainingPeerAndClearsRoomOnDisconnect() throws Exception {
        WebSocketSession seller = participant("ws-1", ROOM_ID, SELLER_ID);
        WebSocketSession buyer = participant("ws-2", ROOM_ID, BUYER_ID);
        WebSocketSession rejoined = participant("ws-3", ROOM_ID, SELLER_ID);
        when(seller.isOpen()).thenReturn(true);

        handler.afterConnectionEstablished(seller);
        handler.afterConnectionEstablished(buyer);
        handler.afterConnectionClosed(buyer, CloseStatus.NORMAL);
        handler.afterConnectionClosed(seller, CloseStatus.NORMAL);
        handler.afterConnectionEstablished(rejoined);

        assertThat(sentMessages(seller, 3).get(2).path("type").asText()).isEqualTo("peer-left");
        // 두 참가자가 모두 나가면 방이 제거되므로 재입장 시 참가자 수가 1로 초기화된다.
        assertThat(sentMessages(rejoined, 1).get(0).path("participantCount").asInt())
                .isEqualTo(1);
    }

    @Test
    void ignoresDisconnectForRoomThatWasNeverEstablished() throws Exception {
        WebSocketSession session = participant("ws-1", ROOM_ID, SELLER_ID);

        handler.afterConnectionClosed(session, CloseStatus.SERVER_ERROR);

        verify(session, never()).sendMessage(any(TextMessage.class));
    }

    @Test
    void keepsReplacementSessionWhenStaleSessionDisconnects() throws Exception {
        WebSocketSession stale = participant("ws-1", ROOM_ID, SELLER_ID);
        WebSocketSession replacement = participant("ws-2", ROOM_ID, SELLER_ID);
        WebSocketSession buyer = participant("ws-3", ROOM_ID, BUYER_ID);
        when(stale.isOpen()).thenReturn(true);
        when(replacement.isOpen()).thenReturn(true);

        handler.afterConnectionEstablished(stale);
        handler.afterConnectionEstablished(replacement);
        handler.afterConnectionClosed(stale, CloseStatus.NORMAL);
        handler.afterConnectionEstablished(buyer);

        // 오래된 세션의 종료 처리가 새 세션을 방에서 제거하면 안 된다.
        assertThat(sentMessages(buyer, 1).get(0).path("participantCount").asInt()).isEqualTo(2);
        assertThat(sentMessages(replacement, 2).get(1).path("type").asText())
                .isEqualTo("peer-ready");
    }

    @Test
    void isolatesParticipantsOfDifferentRtcSessions() throws Exception {
        WebSocketSession first = participant("ws-1", ROOM_ID, SELLER_ID);
        WebSocketSession other = participant("ws-2", 78L, BUYER_ID);

        handler.afterConnectionEstablished(first);
        handler.afterConnectionEstablished(other);

        assertThat(sentMessages(first, 1).get(0).path("participantCount").asInt()).isEqualTo(1);
        assertThat(sentMessages(other, 1).get(0).path("participantCount").asInt()).isEqualTo(1);
    }

    private WebSocketSession participant(String id, long rtcSessionId, long memberId) {
        WebSocketSession session = mock(WebSocketSession.class);
        lenient().when(session.getId()).thenReturn(id);
        when(session.getAttributes())
                .thenReturn(Map.of("rtcSessionId", rtcSessionId, "memberId", memberId));
        return session;
    }

    private List<JsonNode> sentMessages(WebSocketSession session, int expectedCount)
            throws Exception {
        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, times(expectedCount)).sendMessage(captor.capture());
        List<JsonNode> messages = new ArrayList<>();
        for (TextMessage message : captor.getAllValues()) {
            messages.add(objectMapper.readTree(message.getPayload()));
        }
        return messages;
    }
}
