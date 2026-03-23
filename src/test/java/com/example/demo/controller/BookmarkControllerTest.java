package com.example.demo.controller;

import com.example.demo.service.BookmarkService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class BookmarkControllerTest {

    private MockMvc mockMvc;

    @Mock
    private BookmarkService bookmarkService;

    @InjectMocks
    private BookmarkController bookmarkController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(bookmarkController).build();
    }

    @Test
    @DisplayName("즐겨찾기 추가 성공")
    void addSuccess() throws Exception {
        given(bookmarkService.addBookmark("notice-1")).willReturn(Map.of("noticeId", "notice-1", "bookmarked", true));

        mockMvc.perform(post("/api/bookmarks/{noticeId}", "notice-1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.message").value("공고 즐겨찾기 추가 성공"))
                .andExpect(jsonPath("$.data.noticeId").value("notice-1"))
                .andDo(print());
    }

    @Test
    @DisplayName("즐겨찾기 해제 성공")
    void removeSuccess() throws Exception {
        given(bookmarkService.removeBookmark("notice-1")).willReturn(Map.of("noticeId", "notice-1", "bookmarked", false));

        mockMvc.perform(delete("/api/bookmarks/{noticeId}", "notice-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("공고 즐겨찾기 해제 성공"))
                .andExpect(jsonPath("$.data.bookmarked").value(false))
                .andDo(print());
    }

    @Test
    @DisplayName("내 즐겨찾기 목록 조회 성공")
    void listSuccess() throws Exception {
        given(bookmarkService.getMyBookmarks()).willReturn(List.of(Map.of("noticeId", "notice-1")));

        mockMvc.perform(get("/api/bookmarks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("내 즐겨찾기 목록 조회 성공"))
                .andExpect(jsonPath("$.data[0].noticeId").value("notice-1"))
                .andDo(print());
    }
}
