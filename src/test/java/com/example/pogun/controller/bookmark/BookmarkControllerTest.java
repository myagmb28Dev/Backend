package com.example.pogun.controller.bookmark;

import com.example.pogun.dto.bookmark.BookmarkActionResponse;
import com.example.pogun.dto.bookmark.BookmarkSummaryResponse;
import com.example.pogun.service.bookmark.BookmarkService;
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
        UUID noticeId = UUID.fromString("550e8400-e29b-41d4-a716-446655440030");
        given(bookmarkService.addBookmark("notice-1")).willReturn(new BookmarkActionResponse(UUID.randomUUID(), noticeId, true));

        mockMvc.perform(post("/api/bookmarks/{noticeId}", "notice-1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.message").value("공고 즐겨찾기 추가 성공"))
                .andExpect(jsonPath("$.data.noticeId").value(noticeId.toString()))
                .andDo(print());
    }

    @Test
    @DisplayName("즐겨찾기 해제 성공")
    void removeSuccess() throws Exception {
        UUID noticeId = UUID.fromString("550e8400-e29b-41d4-a716-446655440031");
        given(bookmarkService.removeBookmark("notice-1")).willReturn(new BookmarkActionResponse(null, noticeId, false));

        mockMvc.perform(delete("/api/bookmarks/{noticeId}", "notice-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("공고 즐겨찾기 해제 성공"))
                .andExpect(jsonPath("$.data.bookmarked").value(false))
                .andDo(print());
    }

    @Test
    @DisplayName("내 즐겨찾기 목록 조회 성공")
    void listSuccess() throws Exception {
        UUID noticeId = UUID.fromString("550e8400-e29b-41d4-a716-446655440032");
        given(bookmarkService.getMyBookmarks()).willReturn(List.of(
                new BookmarkSummaryResponse(
                        noticeId,
                        "말티즈를 찾습니다",
                        "DOG",
                        "말티즈",
                        Instant.parse("2026-03-20T10:00:00Z"),
                        "서울",
                        "OPEN",
                        "진행중",
                        false,
                        true,
                        Instant.parse("2026-03-21T10:00:00Z")
                )
        ));

        mockMvc.perform(get("/api/bookmarks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("내 즐겨찾기 목록 조회 성공"))
                .andExpect(jsonPath("$.data[0].noticeId").value(noticeId.toString()))
                .andDo(print());
    }
}