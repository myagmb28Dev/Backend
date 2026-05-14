package com.example.pogun.controller.presence;

import com.example.pogun.config.StompAuthChannelInterceptor;
import com.example.pogun.config.WebSocketPrincipal;
import com.example.pogun.service.noticechat.NoticeChatService;
import com.example.pogun.service.user.UserPresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@Slf4j
public class PresenceMessageController {

    private final UserPresenceService userPresenceService;
    private final NoticeChatService noticeChatService;

    @MessageMapping("/presence/ping")
    public void ping(Principal principal, SimpMessageHeaderAccessor headerAccessor) {
        Principal resolvedPrincipal = resolvePrincipal(principal, headerAccessor);
        if (resolvedPrincipal == null || resolvedPrincipal.getName() == null || resolvedPrincipal.getName().isBlank()) {
            log.debug("[presence] ping ignored: unresolved principal sessionId={}", headerAccessor.getSessionId());
            return;
        }
        String firebaseUid = resolvedPrincipal.getName();
        String sessionId = headerAccessor.getSessionId();

        if (sessionId != null && !sessionId.isBlank()) {
            userPresenceService.refreshWebSocketSession(firebaseUid, sessionId);
        }
        if (userPresenceService.touch(firebaseUid)) {
            log.debug("[presence] ping touch updated uid={} sessionId={}", firebaseUid, sessionId);
            noticeChatService.publishPresenceUpdatesByFirebaseUid(firebaseUid);
        } else {
            log.trace("[presence] ping touch skipped uid={} sessionId={}", firebaseUid, sessionId);
        }
    }

    private Principal resolvePrincipal(Principal principal, SimpMessageHeaderAccessor headerAccessor) {
        if (principal != null && principal.getName() != null && !principal.getName().isBlank() && !"anonymousUser".equals(principal.getName())) {
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
