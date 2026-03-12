package com.example.demo.controller;

import java.util.Map;

import com.example.demo.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @PostMapping("/firebase/sign-in")
    @Operation(summary = "Firebase 로그인", description = "Firebase ID Token을 받아 소셜 로그인 처리 및 사용자 정보를 반환합니다. 최초 로그인 시 신규 사용자 여부를 포함합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> firebaseSignIn(@RequestBody Map<String, String> request) {
        Map<String, Object> data = Map.of(
                "provider", request.getOrDefault("provider", "GOOGLE"),
                "firebaseIdToken", request.getOrDefault("firebaseIdToken", "sample-token"),
                "firebaseUid", "firebase_uid_sample_001",
                "email", "user@example.com",
                "isNewUser", true,
                "role", "USER"
        );
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Firebase 로그인 성공", data));
    }

    @PostMapping("/logout")
    @Operation(summary = "로그아웃", description = "사용자 로그아웃 처리(토큰 폐기 등).")
    public ResponseEntity<ApiResponse<Map<String, Object>>> logout() {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "로그아웃 처리 완료", Map.of("revoked", true)));
    }

    @DeleteMapping("/withdraw")
    @Operation(summary = "회원 탈퇴", description = "회원 계정 탈퇴 처리(관련 데이터 정리 등을 수행).")
    public ResponseEntity<ApiResponse<Map<String, Object>>> withdraw() {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "회원 탈퇴 처리 완료", Map.of("status", "WITHDRAWN")));
    }
}


