package com.c203.limit.domain.rtc.websocket;

import com.c203.limit.domain.rtc.service.RtcJoinTokenStore;
import java.util.Map;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class RtcHandshakeInterceptor implements HandshakeInterceptor {
    private final RtcJoinTokenStore tokenStore;

    public RtcHandshakeInterceptor(RtcJoinTokenStore tokenStore) {
        this.tokenStore = tokenStore;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        String token =
                UriComponentsBuilder.fromUri(request.getURI())
                        .build()
                        .getQueryParams()
                        .getFirst("token");
        var ticket = tokenStore.consume(token);
        if (ticket == null) return false;
        attributes.put("rtcSessionId", ticket.sessionId());
        attributes.put("memberId", ticket.memberId());
        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {}
}
