package com.example.demo.controller;

import java.util.Map;

import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.LoginRequest;
import com.example.demo.entity.User;
import com.example.demo.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "인증 API")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "소셜 로그인", description = "Firebase ID Token을 사용하여 소셜 로그인을 수행합니다. 없으면 자동 가입됩니다.")
    public ResponseEntity<ApiResponse<?>> firebaseSignIn(@RequestBody LoginRequest request) {
        String idToken = request.getFirebaseIdToken();
        if (idToken == null || idToken.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.fail(HttpStatus.BAD_REQUEST, "INVALID_TOKEN", "토큰이 비어있습니다.", null));
        }

        try {
            Map<String, Object> data = authService.loginOrSignUp(idToken);
            return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "로그인 성공", data));
        } catch (Exception e) {
            log.error("로그인 중 오류 발생: ", e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.fail(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "인증에 실패했습니다: " + e.getMessage(), null));
        }
    }

    @PostMapping("/logout")
    @Operation(summary = "로그아웃", description = "사용자를 로그아웃 처리합니다.")
    public ResponseEntity<ApiResponse<?>> logout() {
        Map<String, Object> data = authService.logout();
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "로그아웃 처리 완료", data));
    }

    @DeleteMapping("/withdraw")
    @Operation(summary = "회원 탈퇴", description = "회원의 계정 탈퇴 처리를 합니다.")
    public ResponseEntity<ApiResponse<?>> withdraw() {
        Map<String, Object> data = authService.withdraw();
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "회원 탈퇴 처리 완료", data));
    }

    @DeleteMapping("/link/{provider}")
    @Operation(summary = "소셜 계정 연동 해제", description = "지정한 소셜 제공자와의 연동을 해제합니다. 예: GOOGLE")
    public ResponseEntity<ApiResponse<?>> unlinkSocial(@PathVariable String provider) {
        Map<String, Object> data = authService.unlinkSocial(provider);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "소셜 계정 연동 해제 성공", data));
    }
}


