package com.example.demo.controller;

import com.example.demo.dto.NoticeChatMessageRequest;
import com.example.demo.dto.NoticeChatTypingRequest;
import com.example.demo.service.NoticeChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class NoticeChatMessageController {
    private final NoticeChatService noticeChatService;

    @MessageMapping("/chat/send")
    public void send(@Payload NoticeChatMessageRequest request, Principal principal) {
        Map<String, Object> ignored = noticeChatService.sendMessage(principal, request);
    }

    @MessageMapping("/chat/typing")
    public void typing(@Payload NoticeChatTypingRequest request, Principal principal) {
        Map<String, Object> ignored = noticeChatService.sendTypingEvent(principal, request);
    }
}
