package com.example.pogun.config.websocket;

import org.springframework.messaging.simp.SimpMessageHeaderAccessor;

import java.security.Principal;
import java.util.Map;

public final class StompPrincipalResolver {

    private StompPrincipalResolver() {
    }

    public static Principal resolve(Principal principal, SimpMessageHeaderAccessor headerAccessor) {
        if (principal != null
                && principal.getName() != null
                && !principal.getName().isBlank()
                && !"anonymousUser".equals(principal.getName())) {
            return principal;
        }
        Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();
        if (sessionAttributes == null) {
            return principal;
        }
        Object firebaseUid = sessionAttributes.get(StompAuthChannelInterceptor.SESSION_FIREBASE_UID);
        if (firebaseUid instanceof String uid && !uid.isBlank()) {
            return new WebSocketPrincipal(uid);
        }
        return principal;
    }
}
