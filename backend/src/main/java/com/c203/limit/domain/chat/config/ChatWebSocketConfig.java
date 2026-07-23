package com.c203.limit.domain.chat.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import com.c203.limit.domain.chat.websocket.ChatWebSocketAuthInterceptor;
import com.c203.limit.domain.chat.websocket.ChatWebSocketErrorHandler;

@Configuration
@EnableWebSocketMessageBroker
public class ChatWebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final ChatWebSocketAuthInterceptor authInterceptor;
    private final ChatWebSocketErrorHandler errorHandler;
    private final List<String> allowedOriginPatterns;

    public ChatWebSocketConfig(
            ChatWebSocketAuthInterceptor authInterceptor,
            ChatWebSocketErrorHandler errorHandler,
            @Value("${limit.websocket.allowed-origin-patterns:"
                    + "http://localhost:5173,https://l1mit.shop,https://www.l1mit.shop}")
                    List<String> allowedOriginPatterns) {
        this.authInterceptor = authInterceptor;
        this.errorHandler = errorHandler;
        this.allowedOriginPatterns = allowedOriginPatterns;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(allowedOriginPatterns.toArray(String[]::new));
        registry.setErrorHandler(errorHandler);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/pub");
        registry.enableSimpleBroker("/sub", "/queue");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authInterceptor);
    }
}
