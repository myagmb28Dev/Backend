package com.example.demo.controller;

import java.util.Map;

import com.example.demo.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "인증 API")
public class AuthController {

    @PostMapping("/login/google")
    @Operation(summary = "구글 로그인", description = "FCM 토큰을 사용하여 구글 로그인을 합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> firebaseSignIn(@RequestBody Map<String, String> request) {
        Map<String, Object> data = Map.of(
                "provider", request.getOrDefault("provider", "GOOGLE"),
                "firebaseIdToken", request.getOrDefault("firebaseIdToken", "sample-token"),
                "firebaseUid", "firebase_uid_sample_001",
                "email", "user@example.com",
                "isNewUser", true,
                "role", "USER"
        );
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "구글 로그인 성공", data));
    }

    @PostMapping("/logout")
    @Operation(summary = "로그아웃", description = "사용자를 로그아웃 처리합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> logout() {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "로그아웃 처리 완료", Map.of("revoked", true)));
    }

    @DeleteMapping("/withdraw")
    @Operation(summary = "회원 탈퇴", description = "회원의 계정 탈퇴 처리를 합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> withdraw() {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "회원 탈퇴 처리 완료", Map.of("status", "WITHDRAWN")));
    }

    @DeleteMapping("/link/{provider}")
    @Operation(summary = "소셜 계정 연동 해제", description = "지정한 소셜 제공자와의 연동을 해제합니다. 예: GOOGLE")
    public ResponseEntity<ApiResponse<Map<String, Object>>> unlinkSocial(@PathVariable String provider) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "소셜 계정 연동 해제 성공", Map.of("provider", provider, "unlinked", true)));
    }
}


