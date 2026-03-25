package com.example.pogun.controller.noticechat;

import com.example.pogun.dto.noticechat.NoticeChatMessageResponse;
import com.example.pogun.dto.noticechat.NoticeChatRoomCreateResult;
import com.example.pogun.dto.noticechat.NoticeChatRoomResponse;
import com.example.pogun.service.noticechat.NoticeChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class NoticeChatControllerTest {

    private MockMvc mockMvc;

    @Mock
    private NoticeChatService noticeChatService;

    @InjectMocks
    private NoticeChatController noticeChatController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(noticeChatController).build();
    }

    @Test
    @DisplayName("새 공고 채팅방 생성 시 201을 반환한다")
    void createOrGetRoomCreatedSuccess() throws Exception {
        given(noticeChatService.createOrGetRoom("notice-1")).willReturn(new NoticeChatRoomCreateResult(
                true,
                new NoticeChatRoomResponse(
                        UUID.fromString("33333333-3333-3333-3333-333333333333"),
                        UUID.fromString("11111111-1111-1111-1111-111111111111"),
                        "실종 공고",
                        "OPEN",
                        Instant.parse("2026-03-20T13:00:00Z"),
                        Instant.parse("2026-03-20T12:00:00Z"),
                        UUID.fromString("44444444-4444-4444-4444-444444444444"),
                        "주인",
                        0L
                )
        ));

        mockMvc.perform(post("/api/chat/rooms/notice/{noticeId}", "notice-1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("채팅방 생성 성공"))
                .andDo(print());
    }

    @Test
    @DisplayName("기존 공고 채팅방 조회 시 200을 반환한다")
    void createOrGetRoomExistingSuccess() throws Exception {
        given(noticeChatService.createOrGetRoom("notice-1")).willReturn(new NoticeChatRoomCreateResult(
                false,
                new NoticeChatRoomResponse(
                        UUID.fromString("33333333-3333-3333-3333-333333333333"),
                        UUID.fromString("11111111-1111-1111-1111-111111111111"),
                        "실종 공고",
                        "OPEN",
                        Instant.parse("2026-03-20T13:00:00Z"),
                        Instant.parse("2026-03-20T12:00:00Z"),
                        UUID.fromString("44444444-4444-4444-4444-444444444444"),
                        "주인",
                        0L
                )
        ));

        mockMvc.perform(post("/api/chat/rooms/notice/{noticeId}", "notice-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("채팅방 조회 성공"))
                .andDo(print());
    }

    @Test
    @DisplayName("내 채팅방 목록 조회 성공")
    void getRoomsSuccess() throws Exception {
        given(noticeChatService.getRooms()).willReturn(List.of(new NoticeChatRoomResponse(
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "실종 공고",
                "OPEN",
                Instant.parse("2026-03-20T13:00:00Z"),
                Instant.parse("2026-03-20T12:00:00Z"),
                UUID.fromString("44444444-4444-4444-4444-444444444444"),
                "주인",
                1L
        )));

        mockMvc.perform(get("/api/chat/rooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("채팅방 목록 조회 성공"))
                .andExpect(jsonPath("$.data[0].noticeTitle").value("실종 공고"))
                .andDo(print());
    }

    @Test
    @DisplayName("채팅 메시지 목록 조회 성공")
    void getMessagesSuccess() throws Exception {
        given(noticeChatService.getMessages("room-1")).willReturn(List.of(new NoticeChatMessageResponse(
                UUID.fromString("55555555-5555-5555-5555-555555555555"),
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                UUID.fromString("44444444-4444-4444-4444-444444444444"),
                "주인",
                "근처에서 봤어요.",
                false,
                false,
                Instant.parse("2026-03-20T13:00:00Z")
        )));

        mockMvc.perform(get("/api/chat/rooms/{roomId}/messages", "room-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("채팅 메시지 조회 성공"))
                .andExpect(jsonPath("$.data[0].message").value("근처에서 봤어요."))
                .andDo(print());
    }
}
