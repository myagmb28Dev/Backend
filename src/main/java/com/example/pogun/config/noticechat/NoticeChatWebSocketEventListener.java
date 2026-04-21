package com.example.pogun.config;

import com.example.pogun.service.user.UserPresenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
@RequiredArgsConstructor
public class NoticeChatWebSocketEventListener {
    private final UserPresenceService userPresenceService;

    @EventListener
    public void handleConnected(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String firebaseUid = resolveFirebaseUid(accessor);
        if (firebaseUid == null) {
            return;
        }
        userPresenceService.markWebSocketConnected(firebaseUid, accessor.getSessionId());
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String firebaseUid = resolveFirebaseUid(accessor);
        if (firebaseUid == null) {
            return;
        }
        userPresenceService.markWebSocketDisconnected(firebaseUid, accessor.getSessionId());
    }

    private String resolveFirebaseUid(StompHeaderAccessor accessor) {
        if (accessor.getUser() != null && accessor.getUser().getName() != null && !accessor.getUser().getName().isBlank()) {
            return accessor.getUser().getName();
        }
        if (accessor.getSessionAttributes() == null) {
            return null;
        }
        Object firebaseUid = accessor.getSessionAttributes().get(StompAuthChannelInterceptor.SESSION_FIREBASE_UID);
        return firebaseUid instanceof String uid && !uid.isBlank() ? uid : null;
    }
}
