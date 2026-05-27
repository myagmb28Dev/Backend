package com.example.pogun.config.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.context.annotation.Configuration;
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
    private static final long[] BROKER_HEARTBEAT = new long[]{10000, 10000};

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;
    private final WebSocketApiErrorHandler webSocketApiErrorHandler;
    private final TaskScheduler webSocketHeartbeatTaskScheduler;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 단일 서버 MVP 단계라 외부 브로커 없이 simple broker 로 사용자별 queue 와 topic fan-out 을 처리한다.
        registry.enableSimpleBroker("/topic", "/queue")
                .setTaskScheduler(webSocketHeartbeatTaskScheduler)
                .setHeartbeatValue(BROKER_HEARTBEAT);
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
        registry.setPreservePublishOrder(true);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 프론트는 이 endpoint로 STOMP CONNECT 하고, 실제 인증은 inbound interceptor 에서 검증한다.
        registry.setPreserveReceiveOrder(true);
        registry.setErrorHandler(webSocketApiErrorHandler);
        registry.addEndpoint("/ws/chat")
                .setAllowedOriginPatterns(
                        "http://localhost:3000",
                        "http://127.0.0.1:3000",
                        "http://localhost:5173",
                        "http://127.0.0.1:5173",
                        "http://localhost:8080",
                        "http://127.0.0.1:8080",
                        "http://43.201.1.61",
                        "http://43.201.1.61:8080",
                        "http://paw.gbsw.hs.kr",
                        "https://paw.gbsw.hs.kr",
                        "https://pawgen.kro.kr",
                        "http://192.168.*.*:8080",
                        "http://172.*.*.*:8080",
                        "http://10.*.*.*:8080"
                );
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // 모든 클라이언트 SEND/SUBSCRIBE 전에 토큰 기반 Principal 을 심어주는 핵심 지점이다.
        registration.interceptors(stompAuthChannelInterceptor);
    }
}

