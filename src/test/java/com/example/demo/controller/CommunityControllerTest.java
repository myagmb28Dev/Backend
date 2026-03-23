package com.example.demo.controller;

import com.example.demo.dto.CommunityCommentRequest;
import com.example.demo.dto.CommunityPostRequest;
import com.example.demo.service.CommunityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CommunityControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private CommunityService communityService;

    @InjectMocks
    private CommunityController communityController;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders.standaloneSetup(communityController).build();
    }

    @Test
    @DisplayName("커뮤니티 글 목록 조회 성공")
    void listSuccess() throws Exception {
        given(communityService.getPostList("LATEST", "FREE", "dog", "hello"))
                .willReturn(Map.of("items", List.of(Map.of("id", "post-1"))));

        mockMvc.perform(get("/api/community/posts")
                        .param("type", "LATEST")
                        .param("category", "FREE")
                        .param("tag", "dog")
                        .param("q", "hello"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("커뮤니티 글 목록 조회 성공"))
                .andExpect(jsonPath("$.data.items[0].id").value("post-1"))
                .andDo(print());
    }

    @Test
    @DisplayName("커뮤니티 글 생성 성공")
    void createSuccess() throws Exception {
        CommunityPostRequest request = new CommunityPostRequest();
        request.setTitle("제목");
        request.setContent("본문");
        request.setPollOptions(List.of("A", "B"));
        given(communityService.createPost(any(CommunityPostRequest.class))).willReturn(Map.of("id", "post-1"));

        mockMvc.perform(post("/api/community/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("커뮤니티 글 생성 성공"))
                .andExpect(jsonPath("$.data.id").value("post-1"))
                .andDo(print());
    }

    @Test
    @DisplayName("커뮤니티 글 상세 조회 성공")
    void detailSuccess() throws Exception {
        given(communityService.getPostDetail("post-1")).willReturn(Map.of("id", "post-1"));

        mockMvc.perform(get("/api/community/posts/{postId}", "post-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("커뮤니티 글 상세 조회 성공"))
                .andExpect(jsonPath("$.data.id").value("post-1"))
                .andDo(print());
    }

    @Test
    @DisplayName("커뮤니티 글 수정 성공")
    void updateSuccess() throws Exception {
        CommunityPostRequest request = new CommunityPostRequest();
        request.setTitle("수정 제목");
        given(communityService.updatePost(eq("post-1"), any(CommunityPostRequest.class))).willReturn(Map.of("id", "post-1"));

        mockMvc.perform(patch("/api/community/posts/{postId}", "post-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("커뮤니티 글 수정 성공"))
                .andExpect(jsonPath("$.data.id").value("post-1"))
                .andDo(print());
    }

    @Test
    @DisplayName("커뮤니티 글 삭제 성공")
    void deleteSuccess() throws Exception {
        given(communityService.deletePost("post-1")).willReturn(Map.of("deleted", true));

        mockMvc.perform(delete("/api/community/posts/{postId}", "post-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("커뮤니티 글 삭제 성공"))
                .andExpect(jsonPath("$.data.deleted").value(true))
                .andDo(print());
    }

    @Test
    @DisplayName("댓글 목록 조회 성공")
    void commentsSuccess() throws Exception {
        given(communityService.getComments("post-1")).willReturn(List.of(Map.of("id", "comment-1")));

        mockMvc.perform(get("/api/community/posts/{postId}/comments", "post-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("댓글 목록 조회 성공"))
                .andExpect(jsonPath("$.data[0].id").value("comment-1"))
                .andDo(print());
    }

    @Test
    @DisplayName("댓글 작성 성공")
    void createCommentSuccess() throws Exception {
        CommunityCommentRequest request = new CommunityCommentRequest();
        request.setContent("댓글");
        given(communityService.createComment(eq("post-1"), any(CommunityCommentRequest.class))).willReturn(Map.of("id", "comment-1"));

        mockMvc.perform(post("/api/community/posts/{postId}/comments", "post-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("댓글 작성 성공"))
                .andExpect(jsonPath("$.data.id").value("comment-1"))
                .andDo(print());
    }

    @Test
    @DisplayName("투표 참여 성공")
    void voteSuccess() throws Exception {
        Map<String, Object> request = Map.of("optionId", "option-1");
        given(communityService.vote("post-1", request)).willReturn(Map.of("voted", true));

        mockMvc.perform(post("/api/community/posts/{postId}/votes", "post-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("투표 참여 성공"))
                .andExpect(jsonPath("$.data.voted").value(true))
                .andDo(print());
    }

    @Test
    @DisplayName("좋아요 반응 성공")
    void reactSuccess() throws Exception {
        Map<String, String> request = Map.of("reaction", "LIKE");
        given(communityService.react("post-1", request)).willReturn(Map.of("reaction", "LIKE"));

        mockMvc.perform(post("/api/community/posts/{postId}/reactions", "post-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("좋아요/반응 처리 성공"))
                .andExpect(jsonPath("$.data.reaction").value("LIKE"))
                .andDo(print());
    }
}
