package com.example.pogun.controller.noticechat;

import com.example.pogun.config.StompAuthChannelInterceptor;
import com.example.pogun.config.WebSocketPrincipal;
import com.example.pogun.dto.noticechat.NoticeChatMessageRequest;
import com.example.pogun.dto.noticechat.NoticeChatReadRequest;
import com.example.pogun.dto.noticechat.NoticeChatRoomEventRequest;
import com.example.pogun.dto.noticechat.NoticeChatTypingRequest;
import com.example.pogun.service.noticechat.NoticeChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;
/**
 * HTTP/WebSocket 진입점을 담당하는 NoticeChatMessageController이다.
 */

@Controller
@Slf4j
@RequiredArgsConstructor
public class NoticeChatMessageController {
    private final NoticeChatService noticeChatService;

    @MessageMapping("/chat/send")
    public void send(@Valid @Payload NoticeChatMessageRequest request,
                     Principal principal,
                     SimpMessageHeaderAccessor headerAccessor) {
        Principal resolvedPrincipal = resolvePrincipal(principal, headerAccessor);
        log.debug("웹소켓 SEND 수신 destination=/app/chat/send principal={} roomId={}",
                resolvedPrincipal != null ? resolvedPrincipal.getName() : "anonymous",
                request.getRoomId());
        // STOMP payload는 컨트롤러에서 최소 검증만 하고, 저장과 fan-out은 서비스가 담당한다.
        noticeChatService.sendMessage(resolvedPrincipal, request);
    }

    @MessageMapping("/chat/typing")
    public void typing(@Valid @Payload NoticeChatTypingRequest request,
                       Principal principal,
                       SimpMessageHeaderAccessor headerAccessor) {
        Principal resolvedPrincipal = resolvePrincipal(principal, headerAccessor);
        log.debug("웹소켓 SEND 수신 destination=/app/chat/typing principal={} roomId={} isTyping={}",
                resolvedPrincipal != null ? resolvedPrincipal.getName() : "anonymous",
                request.getRoomId(),
                request.getTyping());
        // 입력 중 이벤트는 영속화하지 않고 상대방 화면 동기화용으로만 중계한다.
        noticeChatService.sendTypingEvent(resolvedPrincipal, request);
    }

    @MessageMapping("/chat/enter")
    public void enter(@Valid @Payload NoticeChatRoomEventRequest request,
                      Principal principal,
                      SimpMessageHeaderAccessor headerAccessor) {
        Principal resolvedPrincipal = resolvePrincipal(principal, headerAccessor);
        noticeChatService.enterRoom(resolvedPrincipal, request);
    }

    @MessageMapping("/chat/leave")
    public void leave(@Valid @Payload NoticeChatRoomEventRequest request,
                      Principal principal,
                      SimpMessageHeaderAccessor headerAccessor) {
        Principal resolvedPrincipal = resolvePrincipal(principal, headerAccessor);
        noticeChatService.leaveSocketRoom(resolvedPrincipal, request);
    }

    @MessageMapping("/chat/read")
    public void read(@Payload NoticeChatReadRequest request,
                     Principal principal,
                     SimpMessageHeaderAccessor headerAccessor) {
        Principal resolvedPrincipal = resolvePrincipal(principal, headerAccessor);
        noticeChatService.markRoomAsRead(resolvedPrincipal, request);
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

    @MessageMapping("/chat/enter")
    public void enter(@Valid @Payload NoticeChatRoomEventRequest request, Principal principal) {
        noticeChatService.enterRoom(principal, request);
    }

    @MessageMapping("/chat/leave")
    public void leave(@Valid @Payload NoticeChatRoomEventRequest request, Principal principal) {
        noticeChatService.leaveSocketRoom(principal, request);
    }

    @MessageMapping("/chat/read")
    public void read(@Valid @Payload NoticeChatReadRequest request, Principal principal) {
        noticeChatService.markRoomAsRead(principal, request);
    }
}
