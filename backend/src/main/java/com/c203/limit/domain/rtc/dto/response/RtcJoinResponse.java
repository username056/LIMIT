package com.c203.limit.domain.rtc.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record RtcJoinResponse(
        Long sessionId,
        String signalingUrl,
        String joinToken,
        LocalDateTime expiresAt,
        boolean offerer,
        List<IceServerResponse> iceServers) {
    public record IceServerResponse(String urls, String username, String credential) {}
}
