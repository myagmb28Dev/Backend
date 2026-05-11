package com.example.pogun.controller.admin;

import com.example.pogun.dto.admin.auth.AdminAuthSessionResponse;
import com.example.pogun.dto.admin.auth.AdminLoginRequest;
import com.example.pogun.dto.admin.auth.AdminLoginResponse;
import com.example.pogun.dto.admin.auth.AdminPasskeyCredentialRequest;
import com.example.pogun.dto.admin.auth.AdminPasskeyOptionsResponse;
import com.example.pogun.dto.admin.auth.AdminSessionStateResponse;
import com.example.pogun.dto.common.ApiResponse;
import com.example.pogun.dto.common.ApiResponse.ApiException;
import com.example.pogun.service.adminauth.AdminAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/auth")
@Tag(name = "Admin Auth", description = "관리자 인증 API")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    @PostMapping("/login")
    @Operation(summary = "관리자 Google 로그인", description = "Firebase Google ID 토큰으로 관리자 로그인 후 PassKey 단계 또는 세션을 반환합니다.")
    public ResponseEntity<ApiResponse<AdminLoginResponse>> login(@RequestBody AdminLoginRequest request, HttpServletRequest httpRequest) {
        AdminLoginResponse data;
        try {
            if (request.getFirebaseIdToken() == null || request.getFirebaseIdToken().isBlank()) {
                throw ApiException.badRequest("FIREBASE_ID_TOKEN_REQUIRED", "firebaseIdToken은 필수입니다.");
            }
            data = adminAuthService.loginWithGoogleToken(request.getFirebaseIdToken(), httpRequest);
        } catch (DataAccessException e) {
            throw ApiException.internal("ADMIN_DB_UNAVAILABLE", "DB 연결이 일시적으로 불안정합니다. 잠시 후 다시 시도해주세요.");
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw ApiException.internal("ADMIN_LOGIN_UNHANDLED", e.getClass().getName() + ": " + e.getMessage());
        }
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "관리자 로그인 처리 성공", data));
    }

    @PostMapping("/passkeys/register/options")
    @Operation(summary = "PassKey 등록 옵션 발급", description = "최초 관리자 로그인 후 PassKey 등록용 WebAuthn 옵션을 발급합니다.")
    public ResponseEntity<ApiResponse<AdminPasskeyOptionsResponse>> passkeyRegistrationOptions(HttpServletRequest request) {
        AdminPasskeyOptionsResponse data = adminAuthService.beginPasskeyRegistration(request);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "PassKey 등록 옵션 발급 성공", data));
    }

    @PostMapping("/passkeys/register/verify")
    @Operation(summary = "PassKey 등록 검증", description = "관리자의 PassKey 등록 결과를 검증하고 관리자 세션을 인증 완료 상태로 전환합니다.")
    public ResponseEntity<ApiResponse<AdminAuthSessionResponse>> passkeyRegistrationVerify(
            @Valid @RequestBody AdminPasskeyCredentialRequest request,
            HttpServletRequest httpRequest
    ) {
        AdminAuthSessionResponse data = adminAuthService.finishPasskeyRegistration(request, httpRequest);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "PassKey 등록 성공", data));
    }

    @PostMapping("/mfa/verify")
    @Operation(summary = "PassKey MFA 검증", description = "등록된 PassKey로 관리자 2단계 인증을 완료합니다.")
    public ResponseEntity<ApiResponse<AdminAuthSessionResponse>> verifyMfa(
            @Valid @RequestBody AdminPasskeyCredentialRequest request,
            HttpServletRequest httpRequest
    ) {
        AdminAuthSessionResponse data = adminAuthService.verifyMfa(request, httpRequest);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "PassKey 인증 성공", data));
    }

    @PostMapping("/mfa/options")
    @Operation(summary = "PassKey MFA 옵션 발급", description = "로그인 후 PassKey 인증용 WebAuthn assertion 옵션을 다시 발급합니다.")
    public ResponseEntity<ApiResponse<AdminPasskeyOptionsResponse>> mfaOptions(HttpServletRequest request) {
        AdminPasskeyOptionsResponse data = adminAuthService.startPendingPasskeyAssertion(request);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "PassKey 인증 옵션 발급 성공", data));
    }

    @PostMapping("/stepup/options")
    @Operation(summary = "승격 Step-up 옵션 발급", description = "관리자 승격 API 호출 전 PassKey 재인증 옵션을 발급합니다.")
    public ResponseEntity<ApiResponse<AdminPasskeyOptionsResponse>> stepupOptions(HttpServletRequest request) {
        AdminPasskeyOptionsResponse data = adminAuthService.startPromoteStepUp(request);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "승격 Step-up 옵션 발급 성공", data));
    }

    @PostMapping("/stepup/verify")
    @Operation(summary = "승격 Step-up 검증", description = "관리자 승격 전 PassKey 재인증을 검증하고 단기 승격 권한을 부여합니다.")
    public ResponseEntity<ApiResponse<AdminAuthSessionResponse>> stepupVerify(
            @Valid @RequestBody AdminPasskeyCredentialRequest request,
            HttpServletRequest httpRequest
    ) {
        AdminAuthSessionResponse data = adminAuthService.verifyPromoteStepUp(request, httpRequest);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "승격 Step-up 인증 성공", data));
    }

    @PostMapping("/passkeys/reset")
    @Operation(summary = "PassKey 초기화", description = "MFA 대기 단계에서 저장된 PassKey를 초기화하고 재등록 단계로 전환합니다.")
    public ResponseEntity<ApiResponse<AdminAuthSessionResponse>> resetPasskeys() {
        AdminAuthSessionResponse data = adminAuthService.resetPasskeys();
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "PassKey 초기화 성공", data));
    }

    @GetMapping("/session")
    @Operation(summary = "관리자 세션 조회", description = "관리자 세션 복원에 필요한 인증 상태를 반환합니다.")
    public ResponseEntity<ApiResponse<AdminSessionStateResponse>> session() {
        AdminSessionStateResponse data = adminAuthService.session();
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "관리자 세션 조회 성공", data));
    }

    @PostMapping("/logout")
    @Operation(summary = "관리자 로그아웃", description = "관리자 세션을 즉시 무효화합니다.")
    public ResponseEntity<ApiResponse<Void>> logout() {
        adminAuthService.logout();
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "관리자 로그아웃 성공", null));
    }

    @PostMapping("/refresh")
    @Operation(summary = "관리자 세션 갱신", description = "현재 관리자 세션을 갱신하고 새 accessToken을 발급합니다.")
    public ResponseEntity<ApiResponse<AdminAuthSessionResponse>> refreshSession() {
        AdminAuthSessionResponse data = adminAuthService.refreshSession();
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "관리자 세션 갱신 성공", data));
    }
}
