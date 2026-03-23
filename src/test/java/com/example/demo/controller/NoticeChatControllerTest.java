package com.example.demo.controller;

import com.example.demo.service.NoticeChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

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
    @DisplayName("공고 채팅방 생성 또는 조회 성공")
    void createOrGetRoomSuccess() throws Exception {
        given(noticeChatService.createOrGetRoom("notice-1")).willReturn(Map.of("roomId", "room-1"));

        mockMvc.perform(post("/api/chat/rooms/notice/{noticeId}", "notice-1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("채팅방 조회 성공"))
                .andExpect(jsonPath("$.data.roomId").value("room-1"))
                .andDo(print());
    }

    @Test
    @DisplayName("내 채팅방 목록 조회 성공")
    void getRoomsSuccess() throws Exception {
        given(noticeChatService.getRooms()).willReturn(List.of(Map.of("roomId", "room-1")));

        mockMvc.perform(get("/api/chat/rooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("채팅방 목록 조회 성공"))
                .andExpect(jsonPath("$.data[0].roomId").value("room-1"))
                .andDo(print());
    }

    @Test
    @DisplayName("채팅 메시지 목록 조회 성공")
    void getMessagesSuccess() throws Exception {
        given(noticeChatService.getMessages("room-1")).willReturn(List.of(Map.of("id", "message-1")));

        mockMvc.perform(get("/api/chat/rooms/{roomId}/messages", "room-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("채팅 메시지 조회 성공"))
                .andExpect(jsonPath("$.data[0].id").value("message-1"))
                .andDo(print());
    }
}
