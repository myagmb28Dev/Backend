package com.example.pogun.controller.presence;

import com.example.pogun.config.websocket.StompPrincipalResolver;
import com.example.pogun.service.noticechat.NoticeChatService;
import com.example.pogun.service.user.UserPresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
@Slf4j
public class PresenceMessageController {

    private final UserPresenceService userPresenceService;
    private final NoticeChatService noticeChatService;

    @MessageMapping("/presence/ping")
    public void ping(Principal principal, SimpMessageHeaderAccessor headerAccessor) {
        Principal resolvedPrincipal = StompPrincipalResolver.resolve(principal, headerAccessor);
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
            noticeChatService.publishPresenceEventsByFirebaseUid(firebaseUid);
        } else {
            log.trace("[presence] ping touch skipped uid={} sessionId={}", firebaseUid, sessionId);
        }
    }

}
