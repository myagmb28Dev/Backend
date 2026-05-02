package com.example.pogun.controller.admin;

import com.example.pogun.dto.admin.auth.AdminLocalLoginRequest;
import com.example.pogun.dto.admin.auth.AdminLoginResponse;
import com.example.pogun.dto.common.ApiResponse;
import com.example.pogun.service.adminauth.AdminAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("local")
@RestController
@RequestMapping("/api/admin/auth/local")
@Tag(name = "Admin Auth Local", description = "로컬 테스트 관리자 인증 API")
@RequiredArgsConstructor
public class AdminAuthLocalController {

    private final AdminAuthService adminAuthService;

    @PostMapping("/login")
    @Operation(summary = "로컬 테스트 관리자 로그인", description = "local 프로필에서만 테스트 관리자 세션을 발급합니다.")
    public ResponseEntity<ApiResponse<AdminLoginResponse>> localLogin(
            @RequestBody(required = false) AdminLocalLoginRequest request,
            HttpServletRequest httpRequest
    ) {
        String email = request == null || request.getLocalTestEmail() == null || request.getLocalTestEmail().isBlank()
                ? "playwright-user1@local.dev"
                : request.getLocalTestEmail().trim();
        boolean forcePasskeyEnroll = request == null || !Boolean.FALSE.equals(request.getForcePasskeyEnroll());
        AdminLoginResponse data = adminAuthService.loginLocalTestAdmin(email, forcePasskeyEnroll, httpRequest);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "로컬 테스트 관리자 로그인 성공", data));
    }
}
