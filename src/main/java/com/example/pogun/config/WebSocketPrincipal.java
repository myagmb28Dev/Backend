package com.example.pogun.config;

import java.security.Principal;
/**
 * 애플리케이션 설정을 담당하는 WebSocketPrincipal이다.
 */

public record WebSocketPrincipal(String name) implements Principal {
    @Override
    public String getName() {
        // WebSocket 세션에서는 Firebase uid 를 Principal name 으로 그대로 사용한다.
        return name;
    }
}

