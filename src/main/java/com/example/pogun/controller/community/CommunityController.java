package com.example.pogun.controller.community;

import com.example.pogun.dto.common.ApiResponse;
import com.example.pogun.dto.community.CommunityCommentCreateResponse;
import com.example.pogun.dto.community.CommunityCommentRequest;
import com.example.pogun.dto.community.CommunityCommentResponse;
import com.example.pogun.dto.community.CommunityPostCreateResponse;
import com.example.pogun.dto.community.CommunityPostDeleteResponse;
import com.example.pogun.dto.community.CommunityPostDetailResponse;
import com.example.pogun.dto.community.CommunityPostListResponse;
import com.example.pogun.dto.community.CommunityPostRequest;
import com.example.pogun.dto.community.CommunityPostUpdateResponse;
import com.example.pogun.dto.community.CommunityReactionRequest;
import com.example.pogun.dto.community.CommunityReactionResponse;
import com.example.pogun.dto.community.CommunityVoteRequest;
import com.example.pogun.dto.community.CommunityVoteResponse;
import com.example.pogun.service.community.CommunityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
/**
 * HTTP/WebSocket 진입점을 담당하는 CommunityController이다.
 */

@RestController
@RequestMapping("/api/community/posts")
@Tag(name = "Community", description = "커뮤니티 API")
@RequiredArgsConstructor
public class CommunityController {

    private final CommunityService communityService;

    @GetMapping
    @Operation(summary = "커뮤니티 글 목록 조회", description = "커뮤니티 글 목록을 조회합니다.")
    public ResponseEntity<ApiResponse<CommunityPostListResponse>> list(
            @RequestParam(required = false, defaultValue = "LATEST") String type,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String q
    ) {
        // 정렬 타입과 검색어 같은 화면용 파라미터를 서비스의 목록 조회 규칙으로 위임한다.
        CommunityPostListResponse data = communityService.getPostList(type, category, tag, q);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "커뮤니티 글 목록 조회 성공", data));
    }

    @PostMapping
    @Operation(summary = "커뮤니티 글 생성", description = "커뮤니티 글을 생성합니다.")
    public ResponseEntity<ApiResponse<CommunityPostCreateResponse>> create(@Valid @RequestBody CommunityPostRequest request) {
        CommunityPostCreateResponse data = communityService.createPost(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED, "커뮤니티 글 생성 성공", data));
    }

    @GetMapping("/{postId}")
    @Operation(summary = "커뮤니티 글 상세 조회", description = "커뮤니티 글의 상세 정보를 조회합니다.")
    public ResponseEntity<ApiResponse<CommunityPostDetailResponse>> detail(@PathVariable String postId) {
        CommunityPostDetailResponse data = communityService.getPostDetail(postId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "커뮤니티 글 상세 조회 성공", data));
    }

    @PatchMapping("/{postId}")
    @Operation(summary = "커뮤니티 글 수정", description = "커뮤니티 글을 수정합니다.")
    public ResponseEntity<ApiResponse<CommunityPostUpdateResponse>> update(@PathVariable String postId, @Valid @RequestBody CommunityPostRequest request) {
        CommunityPostUpdateResponse data = communityService.updatePost(postId, request);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "커뮤니티 글 수정 성공", data));
    }

    @DeleteMapping("/{postId}")
    @Operation(summary = "커뮤니티 글 삭제", description = "커뮤니티 글을 삭제합니다.")
    public ResponseEntity<ApiResponse<CommunityPostDeleteResponse>> delete(@PathVariable String postId) {
        CommunityPostDeleteResponse data = communityService.deletePost(postId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "커뮤니티 글 삭제 성공", data));
    }

    @GetMapping("/{postId}/comments")
    @Operation(summary = "댓글 목록 조회", description = "지정된 글의 댓글 목록을 조회합니다.")
    public ResponseEntity<ApiResponse<List<CommunityCommentResponse>>> comments(@PathVariable String postId) {
        List<CommunityCommentResponse> data = communityService.getComments(postId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "댓글 목록 조회 성공", data));
    }

    @PostMapping("/{postId}/comments")
    @Operation(summary = "댓글 작성", description = "지정된 글에 댓글을 작성합니다.")
    public ResponseEntity<ApiResponse<CommunityCommentCreateResponse>> createComment(@PathVariable String postId, @Valid @RequestBody CommunityCommentRequest request) {
        CommunityCommentCreateResponse data = communityService.createComment(postId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED, "댓글 작성 성공", data));
    }

    @PostMapping("/{postId}/votes")
    @Operation(summary = "투표 참여", description = "지정된 글의 투표에 참여합니다.")
    public ResponseEntity<ApiResponse<CommunityVoteResponse>> vote(@PathVariable String postId, @Valid @RequestBody CommunityVoteRequest request) {
        // 투표는 글의 poll 설정과 선택지 유효성을 서비스에서 검증한 뒤 사용자별 참여 내역을 갱신한다.
        CommunityVoteResponse data = communityService.vote(postId, request);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "투표 참여 성공", data));
    }

    @PostMapping("/{postId}/reactions")
    @Operation(summary = "좋아요/반응", description = "지정된 글에 좋아요나 사용자 반응을 추가합니다.")
    public ResponseEntity<ApiResponse<CommunityReactionResponse>> react(@PathVariable String postId, @Valid @RequestBody CommunityReactionRequest request) {
        // 반응은 같은 사용자의 기존 값이 있으면 덮어써서 최신 상태만 유지한다.
        CommunityReactionResponse data = communityService.react(postId, request);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "좋아요/반응 처리 성공", data));
    }
}
