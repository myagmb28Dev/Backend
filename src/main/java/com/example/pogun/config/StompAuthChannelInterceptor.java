package com.example.pogun.config;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;
/**
 * 애플리케이션 설정을 담당하는 StompAuthChannelInterceptor이다.
 */

@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {
    private final FirebaseAuth firebaseAuth;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            // STOMP CONNECT 단계에서 바로 인증을 끝내야 이후 SEND/SUBSCRIBE에서 같은 Principal을 재사용할 수 있다.
            String authorization = accessor.getFirstNativeHeader("Authorization");
            if (authorization == null || !authorization.startsWith("Bearer ")) {
                throw new IllegalArgumentException("웹소켓 인증 토큰이 필요합니다.");
            }

            String token = authorization.substring(7);
            accessor.setUser(new WebSocketPrincipal(resolveUid(token)));
        }
        return message;
    }

    private String resolveUid(String token) {
        try {
            // HTTP 필터와 동일하게 revoke 여부를 검사해 로그아웃된 토큰의 웹소켓 재접속을 차단한다.
            FirebaseToken decodedToken = firebaseAuth.verifyIdToken(token, true);
            return decodedToken.getUid();
        } catch (FirebaseAuthException e) {
            throw new IllegalArgumentException("유효하지 않은 웹소켓 인증 토큰입니다.");
        }
    }
}

