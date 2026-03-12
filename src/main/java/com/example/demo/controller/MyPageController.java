package com.example.demo.controller;

import java.util.List;
import java.util.Map;

import com.example.demo.dto.ApiResponse;
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
@RequestMapping("/api/me")
public class MyPageController {

    private final ProfileService profileService;

    public MyPageController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    @Operation(summary = "마이페이지 - 프로필 조회", description = "내 프로필을 조회합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getProfile() {
        Map<String, Object> data = profileService.getProfile();
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "내 프로필 조회 성공", data));
    }

    @PatchMapping
    @Operation(summary = "마이페이지 - 프로필 수정", description = "내 프로필을 수정합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateProfile(@RequestBody Map<String, Object> request) {
        Map<String, Object> updated = profileService.updateProfile(request);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "내 프로필 수정 성공", updated));
    }

    @GetMapping("/pet-notices")
    @Operation(summary = "마이페이지 - 내 공고 목록", description = "내가 작성한 반려동물 공고 목록을 조회합니다.")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> myPetNotices() {
        List<Map<String, Object>> data = profileService.myPetNotices();
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "내 공고 목록 조회 성공", data));
    }

    @GetMapping("/community-posts")
    @Operation(summary = "마이페이지 - 내 커뮤니티 글 목록", description = "내가 작성한 커뮤니티 글 목록을 조회합니다.")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> myCommunityPosts() {
        List<Map<String, Object>> data = profileService.myCommunityPosts();
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "내 커뮤니티 글 목록 조회 성공", data));
    }
}


