package com.c203.limit.domain.rtc.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class RtcSignalingHandler extends TextWebSocketHandler {
    private static final Set<String> ALLOWED_TYPES =
            Set.of(
                    "offer",
                    "answer",
                    "ice-candidate",
                    "ice-restart",
                    "inspection-request",
                    "hangup");
    private static final int MAX_SIGNAL_BYTES = 64 * 1024;
    private final ObjectMapper objectMapper;
    private final Map<Long, Map<Long, WebSocketSession>> rooms = new ConcurrentHashMap<>();

    public RtcSignalingHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long rtcSessionId = attribute(session, "rtcSessionId");
        Long memberId = attribute(session, "memberId");
        Map<Long, WebSocketSession> participants =
                rooms.computeIfAbsent(rtcSessionId, ignored -> new ConcurrentHashMap<>());
        WebSocketSession previous = participants.put(memberId, session);
        if (previous != null && previous.isOpen()) previous.close(CloseStatus.NORMAL);
        send(session, Map.of("type", "joined", "participantCount", participants.size()));
        notifyPeers(rtcSessionId, memberId, Map.of("type", "peer-ready"));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message)
            throws Exception {
        if (message.getPayloadLength() > MAX_SIGNAL_BYTES) {
            session.close(new CloseStatus(1009, "signal message too big"));
            return;
        }
        JsonNode payload = objectMapper.readTree(message.getPayload());
        String type = payload.path("type").asText();
        if (!ALLOWED_TYPES.contains(type)) {
            send(session, Map.of("type", "error", "code", "UNSUPPORTED_SIGNAL"));
            return;
        }
        notifyPeers(
                attribute(session, "rtcSessionId"),
                attribute(session, "memberId"),
                Map.of("type", type, "payload", payload.path("payload")));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status)
            throws Exception {
        Long rtcSessionId = attribute(session, "rtcSessionId");
        Long memberId = attribute(session, "memberId");
        Map<Long, WebSocketSession> participants = rooms.get(rtcSessionId);
        if (participants == null) return;
        participants.remove(memberId, session);
        notifyPeers(rtcSessionId, memberId, Map.of("type", "peer-left"));
        if (participants.isEmpty()) rooms.remove(rtcSessionId, participants);
    }

    private void notifyPeers(Long rtcSessionId, Long senderId, Object payload) throws IOException {
        Map<Long, WebSocketSession> participants = rooms.get(rtcSessionId);
        if (participants == null) return;
        for (Map.Entry<Long, WebSocketSession> entry : participants.entrySet()) {
            if (!entry.getKey().equals(senderId) && entry.getValue().isOpen()) {
                send(entry.getValue(), payload);
            }
        }
    }

    private void send(WebSocketSession session, Object payload) throws IOException {
        synchronized (session) {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
        }
    }

    private Long attribute(WebSocketSession session, String name) {
        return (Long) session.getAttributes().get(name);
    }
}
