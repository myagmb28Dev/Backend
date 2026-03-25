package com.example.pogun.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
/**
 * 애플리케이션 설정을 담당하는 WebSocketConfig이다.
 */

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 단일 서버 MVP 단계라 외부 브로커 없이 simple broker 로 room topic fan-out 을 처리한다.
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 프론트는 이 endpoint로 STOMP CONNECT 하고, 실제 인증은 inbound interceptor 에서 검증한다.
        registry.addEndpoint("/ws/chat")
                .setAllowedOrigins("http://localhost:3000", "http://127.0.0.1:3000");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // 모든 클라이언트 SEND/SUBSCRIBE 전에 토큰 기반 Principal 을 심어주는 핵심 지점이다.
        registration.interceptors(stompAuthChannelInterceptor);
    }
}

