package com.example.demo.config;

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

@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {
    private final FirebaseAuth firebaseAuth;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
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
            FirebaseToken decodedToken = firebaseAuth.verifyIdToken(token, true);
            return decodedToken.getUid();
        } catch (FirebaseAuthException e) {
            throw new IllegalArgumentException("유효하지 않은 웹소켓 인증 토큰입니다.");
        }
    }
}
