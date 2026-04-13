package com.example.pogun.controller.noticechat;

import com.example.pogun.controller.common.GlobalExceptionHandler;
import com.example.pogun.dto.common.ApiResponse.ApiException;
import com.example.pogun.dto.noticechat.NoticeChatMessageResponse;
import com.example.pogun.dto.noticechat.NoticeChatRoomCreateResult;
import com.example.pogun.dto.noticechat.NoticeChatRoomResponse;
import com.example.pogun.service.noticechat.NoticeChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class NoticeChatControllerTest {

    @Mock
    private NoticeChatService noticeChatService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new NoticeChatController(noticeChatService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createOrGetRoom_returnsCreatedResponse() throws Exception {
        UUID roomId = UUID.randomUUID();
        UUID noticeId = UUID.randomUUID();
        when(noticeChatService.createOrGetRoom(noticeId.toString()))
                .thenReturn(new NoticeChatRoomCreateResult(true, roomResponse(roomId, noticeId)));

        mockMvc.perform(post("/api/chat/rooms/notice/{noticeId}", noticeId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.roomId").value(roomId.toString()))
                .andExpect(jsonPath("$.data.noticeId").value(noticeId.toString()));
    }

    @Test
    void getRooms_returnsRoomList() throws Exception {
        UUID roomId = UUID.randomUUID();
        UUID noticeId = UUID.randomUUID();
        when(noticeChatService.getRooms()).thenReturn(List.of(roomResponse(roomId, noticeId)));

        mockMvc.perform(get("/api/chat/rooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].roomId").value(roomId.toString()))
                .andExpect(jsonPath("$.data[0].lastMessageType").value("TEXT"));
    }

    @Test
    void getRoom_returnsRoomDetail() throws Exception {
        UUID roomId = UUID.randomUUID();
        UUID noticeId = UUID.randomUUID();
        when(noticeChatService.getRoom(roomId.toString())).thenReturn(roomResponse(roomId, noticeId));

        mockMvc.perform(get("/api/chat/rooms/{roomId}", roomId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roomId").value(roomId.toString()));
    }

    @Test
    void getMessages_returnsMessageList() throws Exception {
        UUID roomId = UUID.randomUUID();
        when(noticeChatService.getMessages(roomId.toString())).thenReturn(List.of(messageResponse(roomId)));

        mockMvc.perform(get("/api/chat/rooms/{roomId}/messages", roomId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].roomId").value(roomId.toString()))
                .andExpect(jsonPath("$.data[0].messageType").value("TEXT"));
    }

    @Test
    void sendImages_returnsCreatedResponse() throws Exception {
        UUID roomId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("images", "sample.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[]{1, 2, 3});
        when(noticeChatService.sendImages(eq(roomId.toString()), eq("reply-id"), eq("이미지와 함께 보낸 글"), any(List.class))).thenReturn(messageResponse(roomId));

        mockMvc.perform(multipart("/api/chat/rooms/{roomId}/messages/images", roomId)
                        .file(file)
                        .param("replyToMessageId", "reply-id")
                        .param("message", "이미지와 함께 보낸 글"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.roomId").value(roomId.toString()));
    }

    @Test
    void getRoom_propagatesApiException() throws Exception {
        UUID roomId = UUID.randomUUID();
        when(noticeChatService.getRoom(roomId.toString()))
                .thenThrow(ApiException.notFound("CHAT_ROOM_NOT_FOUND", "채팅방을 찾을 수 없습니다."));

        mockMvc.perform(get("/api/chat/rooms/{roomId}", roomId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CHAT_ROOM_NOT_FOUND"));
    }

    private NoticeChatRoomResponse roomResponse(UUID roomId, UUID noticeId) {
        return new NoticeChatRoomResponse(
                roomId,
                noticeId,
                "실종 공고",
                "OPEN",
                "TEXT",
                Instant.now(),
                "최근 메시지",
                Instant.now(),
                UUID.randomUUID(),
                "상대",
                1L,
                true,
                false,
                false,
                null,
                false,
                null,
                null,
                null
        );
    }

    private NoticeChatMessageResponse messageResponse(UUID roomId) {
        return new NoticeChatMessageResponse(
                UUID.randomUUID(),
                roomId,
                UUID.randomUUID(),
                "문의자",
                "메시지",
                "TEXT",
                List.of(),
                null,
                false,
                true,
                Instant.now(),
                null,
                1L,
                Instant.now()
        );
    }
}
