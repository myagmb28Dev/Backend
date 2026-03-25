package com.example.pogun.controller.auth;

import com.example.pogun.dto.common.ApiResponse;
import com.example.pogun.dto.auth.AuthResponse;
import com.example.pogun.dto.auth.LoginRequest;
import com.example.pogun.dto.auth.LogoutResponse;
import com.example.pogun.dto.auth.SocialUnlinkResponse;
import com.example.pogun.dto.auth.WithdrawResponse;
import com.example.pogun.service.auth.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
/**
 * HTTP/WebSocket 진입점을 담당하는 AuthController이다.
 */

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "인증 API")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "소셜 로그인", description = "Firebase ID Token을 사용하여 소셜 로그인을 수행합니다. 없으면 자동 가입됩니다.")
    public ResponseEntity<ApiResponse<AuthResponse>> firebaseSignIn(@Valid @RequestBody LoginRequest request) {
        // Firebase 인증과 우리 서비스 회원 동기화를 한 번에 수행하는 진입점이다.
        AuthResponse data = authService.loginOrSignUp(request.getFirebaseIdToken());
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "로그인 성공", data));
    }

    @PostMapping("/logout")
    @Operation(summary = "로그아웃", description = "사용자를 로그아웃 처리합니다.")
    public ResponseEntity<ApiResponse<LogoutResponse>> logout() {
        LogoutResponse data = authService.logout();
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "로그아웃 처리 완료", data));
    }

    @DeleteMapping("/withdraw")
    @Operation(summary = "회원 탈퇴", description = "회원의 계정 탈퇴 처리를 합니다.")
    public ResponseEntity<ApiResponse<WithdrawResponse>> withdraw() {
        WithdrawResponse data = authService.withdraw();
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "회원 탈퇴 처리 완료", data));
    }

    @DeleteMapping("/link/{provider}")
    @Operation(summary = "소셜 계정 연동 해제", description = "지정한 소셜 제공자와의 연동을 해제합니다. 예: GOOGLE")
    public ResponseEntity<ApiResponse<SocialUnlinkResponse>> unlinkSocial(@PathVariable String provider) {
        // provider 문자열은 서비스에서 정규화하고, 마지막 연동 수단인지도 함께 검증한다.
        SocialUnlinkResponse data = authService.unlinkSocial(provider);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "소셜 계정 연동 해제 성공", data));
    }
}