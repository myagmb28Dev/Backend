package com.example.pogun.controller.community;

import com.example.pogun.dto.community.CommunityCommentCreateResponse;
import com.example.pogun.dto.community.CommunityCommentDeleteResponse;
import com.example.pogun.dto.community.CommunityCommentRequest;
import com.example.pogun.dto.community.CommunityCommentResponse;
import com.example.pogun.dto.community.CommunityPollResponse;
import com.example.pogun.dto.community.CommunityPostCreateResponse;
import com.example.pogun.dto.community.CommunityPostDeleteResponse;
import com.example.pogun.dto.community.CommunityPostDetailResponse;
import com.example.pogun.dto.community.CommunityPostListResponse;
import com.example.pogun.dto.community.CommunityPostRequest;
import com.example.pogun.dto.community.CommunityPostSummaryResponse;
import com.example.pogun.dto.community.CommunityPostUpdateRequest;
import com.example.pogun.dto.community.CommunityPostUpdateResponse;
import com.example.pogun.dto.community.CommunityReactionRequest;
import com.example.pogun.dto.community.CommunityReactionResponse;
import com.example.pogun.dto.community.CommunityVoteRequest;
import com.example.pogun.dto.community.CommunityVoteResponse;
import com.example.pogun.service.community.CommunityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
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
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CommunityControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper testObjectMapper = new ObjectMapper();

    @Mock
    private CommunityService communityService;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private CommunityController communityController;

    @BeforeEach
    void setUp() {

        mockMvc = MockMvcBuilders.standaloneSetup(communityController).build();
    }

    @Test
    @DisplayName("커뮤니티 글 목록 조회 성공")
    void listSuccess() throws Exception {
        UUID postId = UUID.fromString("550e8400-e29b-41d4-a716-446655440020");
        given(communityService.getPostList("LATEST", "FREE", "dog", "hello", 0, 20))
                .willReturn(new CommunityPostListResponse(
                        1,
                        1,
                        List.of(new CommunityPostSummaryResponse(postId, "제목", "https://example.com/thumb.jpg", "FREE", List.of("dog"), "작성자", 5L, 3L, Instant.parse("2026-03-20T10:00:00Z")))
                ));

        mockMvc.perform(get("/api/community/posts")
                        .param("type", "LATEST")
                        .param("category", "FREE")
                        .param("tag", "dog")
                        .param("q", "hello"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("커뮤니티 글 목록 조회 성공"))
                .andExpect(jsonPath("$.data.items[0].id").value(postId.toString()))
                .andExpect(jsonPath("$.data.items[0].thumbnailImageUrl").value("https://example.com/thumb.jpg"))
                .andDo(print());
    }

    @Test
    @DisplayName("커뮤니티 글 생성 성공")
    void createSuccess() throws Exception {
        UUID postId = UUID.fromString("550e8400-e29b-41d4-a716-446655440021");
        CommunityPostRequest request = new CommunityPostRequest();
        request.setTitle("제목");
        request.setContent("본문");
        request.setPollOptions(List.of("A", "B"));
        given(communityService.createPost(any(CommunityPostRequest.class))).willReturn(new CommunityPostCreateResponse(postId, "제목", "FREE", List.of("dog")));

        mockMvc.perform(post("/api/community/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(testObjectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("커뮤니티 글 생성 성공"))
                .andExpect(jsonPath("$.data.id").value(postId.toString()))
                .andDo(print());
    }

    @Test
    @DisplayName("커뮤니티 글 생성 파일 첨부 성공")
    void createWithFilesSuccess() throws Exception {
        UUID postId = UUID.fromString("550e8400-e29b-41d4-a716-446655440031");
        CommunityPostRequest request = new CommunityPostRequest();
        request.setTitle("제목");
        request.setContent("본문");
        request.setPollOptions(List.of("A", "B"));
        given(communityService.createPost(any(CommunityPostRequest.class), any())).willReturn(new CommunityPostCreateResponse(postId, "제목", "FREE", List.of("dog")));

        MockMultipartFile requestPart = new MockMultipartFile(
                "request",
                "",
                "application/json",
                testObjectMapper.writeValueAsBytes(request)
        );
        MockMultipartFile image1 = new MockMultipartFile("images", "a.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x4A, 0x46});

        mockMvc.perform(multipart("/api/community/posts")
                        .file(requestPart)
                        .file(image1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("커뮤니티 글 생성 성공"))
                .andExpect(jsonPath("$.data.id").value(postId.toString()))
                .andDo(print());
    }
    @Test
    @DisplayName("커뮤니티 글 상세 조회 성공")
    void detailSuccess() throws Exception {
        UUID postId = UUID.fromString("550e8400-e29b-41d4-a716-446655440022");
        given(communityService.getPostDetail("post-1")).willReturn(new CommunityPostDetailResponse(
                postId,
                "제목",
                "본문",
                "FREE",
                List.of("dog"),
                "작성자",
                10L,
                4L,
                Instant.parse("2026-03-20T10:00:00Z"),
                new CommunityPollResponse("질문", List.of("A", "B")),
                List.of("https://example.com/image.jpg")
        ));

        mockMvc.perform(get("/api/community/posts/{postId}", "post-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("커뮤니티 글 상세 조회 성공"))
                .andExpect(jsonPath("$.data.id").value(postId.toString()))
                .andDo(print());
    }

    @Test
    @DisplayName("커뮤니티 글 수정 파일 첨부 성공")
    void updateWithFilesSuccess() throws Exception {
        UUID postId = UUID.fromString("550e8400-e29b-41d4-a716-446655440032");
        CommunityPostUpdateRequest request = new CommunityPostUpdateRequest();
        request.setTitle("수정 제목");
        request.setCategory("FREE");
        request.setTags(List.of("dog"));
        given(communityService.updatePost(eq("post-1"), any(CommunityPostUpdateRequest.class), any())).willReturn(new CommunityPostUpdateResponse(postId, true, "FREE", List.of("dog")));

        MockMultipartFile requestPart = new MockMultipartFile(
                "request",
                "",
                "application/json",
                testObjectMapper.writeValueAsBytes(request)
        );
        MockMultipartFile image1 = new MockMultipartFile("images", "a.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x4A, 0x46});

        mockMvc.perform(multipart("/api/community/posts/{postId}", "post-1")
                        .file(requestPart)
                        .file(image1)
                        .with(requestBuilder -> {
                            requestBuilder.setMethod("PATCH");
                            return requestBuilder;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("커뮤니티 글 수정 성공"))
                .andExpect(jsonPath("$.data.id").value(postId.toString()))
                .andDo(print());
    }
    @Test
    @DisplayName("커뮤니티 글 수정 성공")
    void updateSuccess() throws Exception {
        UUID postId = UUID.fromString("550e8400-e29b-41d4-a716-446655440023");
        CommunityPostUpdateRequest request = new CommunityPostUpdateRequest();
        request.setTitle("수정 제목");
        request.setCategory("FREE");
        request.setTags(List.of("dog"));
        given(communityService.updatePost(eq("post-1"), any(CommunityPostUpdateRequest.class))).willReturn(new CommunityPostUpdateResponse(postId, true, "FREE", List.of("dog")));

        mockMvc.perform(patch("/api/community/posts/{postId}", "post-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(testObjectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("커뮤니티 글 수정 성공"))
                .andExpect(jsonPath("$.data.id").value(postId.toString()))
                .andDo(print());
    }

    @Test
    @DisplayName("커뮤니티 글 삭제 성공")
    void deleteSuccess() throws Exception {
        UUID postId = UUID.fromString("550e8400-e29b-41d4-a716-446655440024");
        given(communityService.deletePost("post-1")).willReturn(new CommunityPostDeleteResponse(postId, true));

        mockMvc.perform(delete("/api/community/posts/{postId}", "post-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("커뮤니티 글 삭제 성공"))
                .andExpect(jsonPath("$.data.deleted").value(true))
                .andDo(print());
    }

    @Test
    @DisplayName("댓글 삭제 성공")
    void deleteCommentSuccess() throws Exception {
        UUID commentId = UUID.fromString("550e8400-e29b-41d4-a716-446655440025");
        UUID postId = UUID.fromString("550e8400-e29b-41d4-a716-446655440026");
        given(communityService.deleteComment("post-1", "comment-1")).willReturn(new CommunityCommentDeleteResponse(commentId, postId, true));

        mockMvc.perform(delete("/api/community/posts/{postId}/comments/{commentId}", "post-1", "comment-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("댓글 삭제 성공"))
                .andExpect(jsonPath("$.data.deleted").value(true))
                .andDo(print());
    }

    @Test
    @DisplayName("댓글 목록 조회 성공")
    void commentsSuccess() throws Exception {
        UUID commentId = UUID.fromString("550e8400-e29b-41d4-a716-446655440025");
        given(communityService.getComments("post-1")).willReturn(List.of(
                new CommunityCommentResponse(commentId, "댓글", "작성자", Instant.parse("2026-03-20T11:00:00Z"), null, List.of())
        ));

        mockMvc.perform(get("/api/community/posts/{postId}/comments", "post-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("댓글 목록 조회 성공"))
                .andExpect(jsonPath("$.data[0].id").value(commentId.toString()))
                .andDo(print());
    }

    @Test
    @DisplayName("댓글 작성 성공")
    void createCommentSuccess() throws Exception {
        UUID commentId = UUID.fromString("550e8400-e29b-41d4-a716-446655440026");
        UUID postId = UUID.fromString("550e8400-e29b-41d4-a716-446655440027");
        CommunityCommentRequest request = new CommunityCommentRequest();
        request.setContent("댓글");
        given(communityService.createComment(eq("post-1"), any(CommunityCommentRequest.class))).willReturn(new CommunityCommentCreateResponse(commentId, postId, null));

        mockMvc.perform(post("/api/community/posts/{postId}/comments", "post-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(testObjectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("댓글 작성 성공"))
                .andExpect(jsonPath("$.data.id").value(commentId.toString()))
                .andDo(print());
    }

    @Test
    @DisplayName("투표 참여 성공")
    void voteSuccess() throws Exception {
        UUID postId = UUID.fromString("550e8400-e29b-41d4-a716-446655440028");
        CommunityVoteRequest request = new CommunityVoteRequest();
        request.setOption("option-1");
        given(communityService.vote(eq("post-1"), any(CommunityVoteRequest.class))).willReturn(new CommunityVoteResponse(postId, "option-1", "투표 참여 성공"));

        mockMvc.perform(post("/api/community/posts/{postId}/votes", "post-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(testObjectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("투표 참여 성공"))
                .andExpect(jsonPath("$.data.selection").value("option-1"))
                .andDo(print());
    }

    @Test
    @DisplayName("좋아요 반응 성공")
    void reactSuccess() throws Exception {
        UUID postId = UUID.fromString("550e8400-e29b-41d4-a716-446655440029");
        CommunityReactionRequest request = new CommunityReactionRequest();
        request.setReaction("LIKE");
        given(communityService.react(eq("post-1"), any(CommunityReactionRequest.class))).willReturn(new CommunityReactionResponse(postId, "LIKE", "좋아요/반응 처리 성공"));

        mockMvc.perform(post("/api/community/posts/{postId}/reactions", "post-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(testObjectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("좋아요/반응 처리 성공"))
                .andExpect(jsonPath("$.data.reaction").value("LIKE"))
                .andDo(print());
    }
}
















