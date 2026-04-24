package com.example.pogun.controller.noticechat;

import com.example.pogun.dto.noticechat.NoticeChatMessageRequest;
import com.example.pogun.service.noticechat.NoticeChatService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;

import java.security.Principal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoticeChatMessageControllerTest {

    @Mock
    private NoticeChatService noticeChatService;

    @InjectMocks
    private NoticeChatMessageController controller;

    @Test
    void send_whenServiceThrows_publishesNackAndRethrows() {
        UUID roomId = UUID.randomUUID();
        NoticeChatMessageRequest request = new NoticeChatMessageRequest();
        request.setRoomId(roomId);
        request.setMessage("테스트 메시지");
        request.setClientMessageId("client-message-1");
        Principal principal = () -> "firebase-uid";
        SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.create();

        when(noticeChatService.sendMessage(any(), eq(request))).thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> controller.send(request, principal, headerAccessor))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");

        verify(noticeChatService).publishMessageNack(
                "firebase-uid",
                roomId,
                "client-message-1",
                "MESSAGE_SEND_FAILED",
                "boom"
        );
    }

    @Test
    void send_whenPrincipalMissing_doesNotPublishNack() {
        UUID roomId = UUID.randomUUID();
        NoticeChatMessageRequest request = new NoticeChatMessageRequest();
        request.setRoomId(roomId);
        request.setMessage("테스트 메시지");
        request.setClientMessageId("client-message-2");
        Principal anonymous = () -> "";
        SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.create();

        when(noticeChatService.sendMessage(any(), eq(request))).thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> controller.send(request, anonymous, headerAccessor))
                .isInstanceOf(IllegalStateException.class);

        verify(noticeChatService, never()).publishMessageNack(any(), any(), any(), any(), any());
    }
}
