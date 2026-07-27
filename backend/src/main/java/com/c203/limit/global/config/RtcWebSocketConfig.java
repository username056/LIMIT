package com.c203.limit.global.config;

import com.c203.limit.domain.rtc.websocket.RtcHandshakeInterceptor;
import com.c203.limit.domain.rtc.websocket.RtcSignalingHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class RtcWebSocketConfig implements WebSocketConfigurer {
    private final RtcSignalingHandler handler;
    private final RtcHandshakeInterceptor interceptor;
    private final String[] allowedOrigins;

    public RtcWebSocketConfig(
            RtcSignalingHandler handler,
            RtcHandshakeInterceptor interceptor,
            @Value("${limit.rtc.allowed-origin-patterns:*}") String allowedOrigins) {
        this.handler = handler;
        this.interceptor = interceptor;
        this.allowedOrigins = allowedOrigins.split(",");
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/rtc")
                .addInterceptors(interceptor)
                .setAllowedOriginPatterns(allowedOrigins);
    }
}
