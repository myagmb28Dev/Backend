package com.example.pogun.controller.user;

import com.example.pogun.dto.common.ApiResponse;
import com.example.pogun.dto.user.UpdateProfileRequest;
import com.example.pogun.dto.user.UserAvailabilityResponse;
import com.example.pogun.dto.user.UserAvailabilityUpdateRequest;
import com.example.pogun.dto.user.UserCommunityPostSummaryResponse;
import com.example.pogun.dto.user.UserFollowResponse;
import com.example.pogun.dto.user.UserLocationUpdateRequest;
import com.example.pogun.dto.user.UserPetNoticeSummaryResponse;
import com.example.pogun.dto.user.UserProfileResponse;
import com.example.pogun.service.user.UserFollowService;
import com.example.pogun.service.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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
    private final UserFollowService userFollowService;

    @GetMapping("/me")
    @Operation(summary = "프로필 조회", description = "로그인한 사용자의 프로필 정보를 조회합니다.")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile() {
        UserProfileResponse data = userService.getProfile();
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "프로필 조회 성공", data));
    }

    @GetMapping("/me/availability")
    @Operation(summary = "내 가용 상태 조회", description = "로그인한 사용자의 전역 가용 상태를 조회합니다.")
    public ResponseEntity<ApiResponse<UserAvailabilityResponse>> getAvailability() {
        UserAvailabilityResponse data = userService.getAvailability();
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "가용 상태 조회 성공", data));
    }

    @PatchMapping("/me/availability")
    @Operation(summary = "내 가용 상태 변경", description = "로그인한 사용자의 전역 가용 상태를 ONLINE, IDLE, OFFLINE 중 하나로 변경합니다.")
    public ResponseEntity<ApiResponse<UserAvailabilityResponse>> updateAvailability(@Valid @RequestBody UserAvailabilityUpdateRequest request) {
        UserAvailabilityResponse data = userService.updateAvailability(request);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "가용 상태 변경 성공", data));
    }

    @PatchMapping("/me")
    @Operation(summary = "프로필 수정", description = "로그인한 사용자의 프로필 정보를 수정합니다.")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        // 프로필 수정은 현재 로그인한 사용자 한 명만 대상으로 하며, 서비스에서 변경 가능한 필드만 반영한다.
        UserProfileResponse updated = userService.updateProfile(request);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "프로필 수정 성공", updated));
    }

    @PatchMapping(value = "/me", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "프로필 수정(파일 첨부)", description = "multipart/form-data 요청으로 `request` JSON과 `profileImage` 파일을 함께 받아 로그인한 사용자의 프로필과 S3 이미지를 수정합니다.")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfileWithFile(
            @Valid @RequestPart("request") UpdateProfileRequest request,
            @RequestPart(value = "profileImage", required = false) MultipartFile profileImage
    ) {
        UserProfileResponse updated = userService.updateProfile(request, profileImage);
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

    @PostMapping("/{userId}/follow")
    @Operation(summary = "사용자 팔로우", description = "지정한 사용자를 팔로우합니다.")
    public ResponseEntity<ApiResponse<UserFollowResponse>> follow(@PathVariable String userId) {
        UserFollowResponse data = userFollowService.follow(userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED, "팔로우 성공", data));
    }

    @PatchMapping("/me/location")
    @Operation(summary = "내 위치 재설정", description = "로그인한 사용자의 좌표(x,y)로 행정구역을 다시 계산해 위치 정보를 갱신합니다.")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateLocation(@Valid @RequestBody UserLocationUpdateRequest request) {
        UserProfileResponse updated = userService.updateLocation(request);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "위치 재설정 성공", updated));
    }

    @DeleteMapping("/{userId}/follow")
    @Operation(summary = "사용자 언팔로우", description = "지정한 사용자를 언팔로우합니다.")
    public ResponseEntity<ApiResponse<Void>> unfollow(@PathVariable String userId) {
        userFollowService.unfollow(userId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "언팔로우 성공", null));
    }

    @GetMapping("/me/following")
    @Operation(summary = "내 팔로잉 목록 조회", description = "내가 팔로우한 사용자 목록을 조회합니다.")
    public ResponseEntity<ApiResponse<List<UserFollowResponse>>> following() {
        List<UserFollowResponse> data = userFollowService.getFollowing();
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "팔로잉 목록 조회 성공", data));
    }

    @GetMapping("/me/followers")
    @Operation(summary = "내 팔로워 목록 조회", description = "나를 팔로우한 사용자 목록을 조회합니다.")
    public ResponseEntity<ApiResponse<List<UserFollowResponse>>> followers() {
        List<UserFollowResponse> data = userFollowService.getFollowers();
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "팔로워 목록 조회 성공", data));
    }
}
