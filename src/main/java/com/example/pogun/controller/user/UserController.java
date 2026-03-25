package com.example.pogun.controller.user;

import com.example.pogun.dto.common.ApiResponse;
import com.example.pogun.dto.user.UpdateProfileRequest;
import com.example.pogun.dto.user.UserCommunityPostSummaryResponse;
import com.example.pogun.dto.user.UserPetNoticeSummaryResponse;
import com.example.pogun.dto.user.UserProfileResponse;
import com.example.pogun.service.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
/**
 * HTTP/WebSocket 진입점을 담당하는 UserController이다.
 */

@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "사용자 API")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @Operation(summary = "프로필 조회", description = "로그인한 사용자의 프로필 정보를 조회합니다.")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile() {
        UserProfileResponse data = userService.getProfile();
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "프로필 조회 성공", data));
    }

    @PatchMapping("/me")
    @Operation(summary = "프로필 수정", description = "로그인한 사용자의 프로필 정보를 수정합니다.")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        // 프로필 수정은 현재 로그인한 사용자 한 명만 대상으로 하며, 서비스에서 변경 가능한 필드만 반영한다.
        UserProfileResponse updated = userService.updateProfile(request);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "프로필 수정 성공", updated));
    }

    @GetMapping("/me/posts")
    @Operation(summary = "작성한 실종 공고 조회", description = "사용자가 작성한 실종 공고 목록을 조회합니다.")
    public ResponseEntity<ApiResponse<List<UserPetNoticeSummaryResponse>>> myPosts() {
        List<UserPetNoticeSummaryResponse> data = userService.myPetNotices();
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "실종 공고 목록 조회 성공", data));
    }

    @GetMapping("/me/community-posts")
    @Operation(summary = "작성한 커뮤니티 글 조회", description = "사용자가 작성한 커뮤니티 글 목록을 조회합니다.")
    public ResponseEntity<ApiResponse<List<UserCommunityPostSummaryResponse>>> myCommunityPosts() {
        List<UserCommunityPostSummaryResponse> data = userService.myCommunityPosts();
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "커뮤니티 글 목록 조회 성공", data));
    }
}