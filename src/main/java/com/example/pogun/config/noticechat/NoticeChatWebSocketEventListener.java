package com.example.pogun.config.noticechat;

import com.example.pogun.config.websocket.StompAuthChannelInterceptor;
import com.example.pogun.service.noticechat.NoticeChatService;
import com.example.pogun.service.user.UserPresenceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
@Slf4j
public class NoticeChatWebSocketEventListener {
    private final ObjectProvider<NoticeChatService> noticeChatServiceProvider;
    private final UserPresenceService userPresenceService;

    public NoticeChatWebSocketEventListener(
            ObjectProvider<NoticeChatService> noticeChatServiceProvider,
            UserPresenceService userPresenceService
    ) {
        this.noticeChatServiceProvider = noticeChatServiceProvider;
        this.userPresenceService = userPresenceService;
    }

    @EventListener
    public void handleConnected(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String firebaseUid = resolveFirebaseUid(accessor);
        if (firebaseUid == null) {
            log.debug("[ws-event] connected event ignored: missing firebaseUid sessionId={}", accessor.getSessionId());
            return;
        }
        log.info("[ws-event] connected uid={} sessionId={}", firebaseUid, accessor.getSessionId());
        userPresenceService.markWebSocketConnected(firebaseUid, accessor.getSessionId());
        noticeChatServiceProvider.getObject().publishPresenceUpdatesByFirebaseUid(firebaseUid);
        noticeChatServiceProvider.getObject().publishPresenceEventsByFirebaseUid(firebaseUid);
        log.debug("[ws-event] presence broadcast published for connect uid={}", firebaseUid);
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String firebaseUid = resolveFirebaseUid(accessor);
        if (firebaseUid == null) {
            log.debug("[ws-event] disconnect event ignored: missing firebaseUid sessionId={}", accessor.getSessionId());
            return;
        }
        log.info("[ws-event] disconnected uid={} sessionId={} closeStatus={}", firebaseUid, accessor.getSessionId(), event.getCloseStatus());
        userPresenceService.markWebSocketDisconnected(firebaseUid, accessor.getSessionId());
        noticeChatServiceProvider.getObject().publishPresenceUpdatesByFirebaseUid(firebaseUid);
        noticeChatServiceProvider.getObject().publishPresenceEventsByFirebaseUid(firebaseUid);
        log.debug("[ws-event] presence broadcast published for disconnect uid={}", firebaseUid);
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
