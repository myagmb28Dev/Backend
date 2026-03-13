package com.example.demo.controller;

import java.util.List;
import java.util.Map;

import com.example.demo.dto.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.demo.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "사용자 API")
public class UserController {

    private final ProfileService profileService;

    public UserController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/me")
    @Operation(summary = "프로필 조회", description = "로그인한 사용자의 프로필 정보를 조회합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getProfile() {
        Map<String, Object> data = profileService.getProfile();
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "프로필 조회 성공", data));
    }

    @PatchMapping("/me")
    @Operation(summary = "프로필 수정", description = "로그인한 사용자의 프로필 정보를 수정합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateProfile(@RequestBody Map<String, Object> request) {
        Map<String, Object> updated = profileService.updateProfile(request);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "프로필 수정 성공", updated));
    }

    @GetMapping("/me/posts")
    @Operation(summary = "작성한 실종 공고 조회", description = "사용자가 작성한 실종 공고 목록을 조회합니다.")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> myPosts() {
        List<Map<String, Object>> data = profileService.myPetNotices();
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "실종 공고 목록 조회 성공", data));
    }

    @GetMapping("/me/community-posts")
    @Operation(summary = "작성한 커뮤니티 글 조회", description = "사용자가 작성한 커뮤니티 글 목록을 조회합니다.")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> myCommunityPosts() {
        List<Map<String, Object>> data = profileService.myCommunityPosts();
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "커뮤니티 글 목록 조회 성공", data));
    }
}



