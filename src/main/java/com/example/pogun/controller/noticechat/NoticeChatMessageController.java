package com.example.pogun.controller.noticechat;

import com.example.pogun.dto.noticechat.NoticeChatMessageRequest;
import com.example.pogun.dto.noticechat.NoticeChatTypingRequest;
import com.example.pogun.service.noticechat.NoticeChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;
/**
 * HTTP/WebSocket 진입점을 담당하는 NoticeChatMessageController이다.
 */

@Controller
@Slf4j
@RequiredArgsConstructor
public class NoticeChatMessageController {
    private final NoticeChatService noticeChatService;

    @MessageMapping("/chat/send")
    public void send(@Valid @Payload NoticeChatMessageRequest request, Principal principal) {
        log.info("웹소켓 SEND 수신 destination=/app/chat/send principal={} roomId={}",
                principal != null ? principal.getName() : "anonymous",
                request.getRoomId());
        // STOMP payload는 컨트롤러에서 최소 검증만 하고, 저장과 fan-out은 서비스가 담당한다.
        noticeChatService.sendMessage(principal, request);
    }

    @MessageMapping("/chat/typing")
    public void typing(@Valid @Payload NoticeChatTypingRequest request, Principal principal) {
        log.info("웹소켓 SEND 수신 destination=/app/chat/typing principal={} roomId={} isTyping={}",
                principal != null ? principal.getName() : "anonymous",
                request.getRoomId(),
                request.getTyping());
        // 입력 중 이벤트는 영속화하지 않고 상대방 화면 동기화용으로만 중계한다.
        noticeChatService.sendTypingEvent(principal, request);
    }
}