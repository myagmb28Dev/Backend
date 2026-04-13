package com.example.pogun.config;

import com.example.pogun.service.noticechat.NoticeChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
@RequiredArgsConstructor
public class NoticeChatWebSocketEventListener {
    private final NoticeChatService noticeChatService;

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        if (accessor.getUser() != null) {
            noticeChatService.markUserOffline(accessor.getUser().getName());
            return;
        }
        if (accessor.getSessionAttributes() == null) {
            return;
        }
        Object firebaseUid = accessor.getSessionAttributes().get(StompAuthChannelInterceptor.SESSION_FIREBASE_UID);
        if (firebaseUid instanceof String uid) {
            noticeChatService.markUserOffline(uid);
        }
    }
}
