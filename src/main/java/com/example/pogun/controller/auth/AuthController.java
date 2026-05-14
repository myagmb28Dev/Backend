package com.example.pogun.controller.auth;

import com.example.pogun.dto.common.ApiResponse;
import com.example.pogun.dto.auth.AuthResponse;
import com.example.pogun.dto.auth.LoginRequest;
import com.example.pogun.dto.auth.LogoutResponse;
import com.example.pogun.dto.auth.OnboardingCompleteRequest;
import com.example.pogun.dto.auth.OnboardingMapConfigResponse;
import com.example.pogun.dto.auth.SocialUnlinkResponse;
import com.example.pogun.dto.auth.TokenRefreshRequest;
import com.example.pogun.dto.auth.TokenRefreshResponse;
import com.example.pogun.dto.auth.WithdrawResponse;
import com.example.pogun.config.KakaoLocalProperties;
import com.example.pogun.service.auth.AuthService;
import com.example.pogun.service.auth.FirebaseTokenRefreshService;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
    private static final String REFRESH_COOKIE_NAME = "pogun_refresh_token";

    private final AuthService authService;
    private final FirebaseTokenRefreshService firebaseTokenRefreshService;
    private final KakaoLocalProperties kakaoLocalProperties;

    @PostMapping("/login")
    @Operation(summary = "소셜 로그인", description = "Firebase ID Token을 사용하여 소셜 로그인을 수행합니다. 없으면 자동 가입됩니다.")
    public ResponseEntity<ApiResponse<AuthResponse>> firebaseSignIn(@Valid @RequestBody LoginRequest request,
                                                                    HttpServletRequest httpRequest) {
        long startTime = System.currentTimeMillis();
        log.info("[LOGIN_ENDPOINT] /api/auth/login request received");
        try {
            // Firebase 인증은 완료하되, 신규 사용자는 정식 회원 대신 온보딩 대기 상태로 둔다.
            AuthResponse data = authService.loginOrSignUp(request.getFirebaseIdToken(), httpRequest);
            long endTime = System.currentTimeMillis();
            log.info("[LOGIN_ENDPOINT] /api/auth/login completed in {}ms", endTime - startTime);
            return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "로그인 성공", data));
        } catch (Exception e) {
            long errorTime = System.currentTimeMillis();
            log.error("[LOGIN_ENDPOINT] /api/auth/login failed after {}ms: {}", errorTime - startTime, e.getMessage());
            throw e;
        }
    }

    @PostMapping("/admin/login")
    @Operation(summary = "관리자 로그인", description = "Firebase 이메일/비밀번호 로그인으로 발급된 ID Token을 검증하고 기존 관리자 계정만 로그인시킵니다.")
    public ResponseEntity<ApiResponse<AuthResponse>> adminSignIn(@Valid @RequestBody LoginRequest request,
                                                                 HttpServletRequest httpRequest) {
        AuthResponse data = authService.loginAdmin(request.getFirebaseIdToken(), httpRequest);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "관리자 로그인 성공", data));
    }

    @PostMapping("/refresh")
    @Operation(
            summary = "Firebase 토큰 갱신",
            description = """
                    Firebase Refresh Token으로 새 Firebase ID Token과 새 Refresh Token을 발급합니다.

                    refreshToken은 요청 body의 refreshToken 필드로 전달할 수 있고, body가 비어 있으면 HttpOnly 쿠키 pogun_refresh_token에서 읽습니다.
                    성공 시 응답 data.refreshToken과 동일한 값을 HttpOnly 쿠키 pogun_refresh_token에도 다시 저장합니다.
                    프론트는 data.idToken을 즉시 현재 Firebase ID Token으로 교체하고, data.refreshToken을 로컬 세션 저장소에 갱신해야 합니다.
                    expiresIn은 초 단위이며 일반적으로 3600입니다.
                    """
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "토큰 갱신 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = TokenRefreshResponse.class),
                            examples = @ExampleObject(
                                    name = "success",
                                    value = """
                                            {
                                              "ok": true,
                                              "status": 200,
                                              "message": "토큰 갱신 성공",
                                              "data": {
                                                "idToken": "eyJhbGciOiJSUzI1NiIsImtpZCI6...",
                                                "refreshToken": "AEu4IL1...new-refresh-token",
                                                "expiresIn": 3600,
                                                "firebaseUid": "qMm6je2Jaec97hJ5DWV9wpwYeYp2",
                                                "projectId": "pogun-local"
                                              },
                                              "error": null,
                                              "meta": {
                                                "requestId": "req_123456789abc",
                                                "timestamp": "2026-05-07T08:00:00Z"
                                              }
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "리프레시 토큰 누락 또는 Firebase 갱신 실패",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "refresh-token-required",
                                    value = """
                                            {
                                              "ok": false,
                                              "status": 401,
                                              "message": "리프레시 토큰이 필요합니다.",
                                              "data": null,
                                              "error": {
                                                "code": "TOKEN_REFRESH_REQUIRED",
                                                "message": "리프레시 토큰이 필요합니다.",
                                                "detail": null
                                              },
                                              "meta": {
                                                "requestId": "req_123456789abc",
                                                "timestamp": "2026-05-07T08:00:00Z"
                                              }
                                            }
                                            """
                            )
                    )
            )
    })
    public ResponseEntity<ApiResponse<TokenRefreshResponse>> refreshFirebaseToken(
            @Valid @RequestBody TokenRefreshRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse response
    ) {
        String refreshToken = resolveRefreshToken(request, servletRequest);
        TokenRefreshResponse data = firebaseTokenRefreshService.refresh(refreshToken);
        boolean isSecureRequest = servletRequest.isSecure()
                || "https".equalsIgnoreCase(servletRequest.getHeader("X-Forwarded-Proto"));
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, data.refreshToken())
                .httpOnly(true)
                .secure(isSecureRequest)
                .sameSite("Strict")
                .path("/api/auth")
                .maxAge(60L * 60L * 24L * 30L)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "토큰 갱신 성공", data));
    }

    private String resolveRefreshToken(TokenRefreshRequest request, HttpServletRequest servletRequest) {
        if (request != null && StringUtils.hasText(request.getRefreshToken())) {
            return request.getRefreshToken().trim();
        }
        if (servletRequest.getCookies() == null) {
            throw com.example.pogun.dto.common.ApiResponse.ApiException.unauthorized("TOKEN_REFRESH_REQUIRED", "리프레시 토큰이 필요합니다.");
        }
        for (var cookie : servletRequest.getCookies()) {
            if (REFRESH_COOKIE_NAME.equals(cookie.getName()) && StringUtils.hasText(cookie.getValue())) {
                return cookie.getValue().trim();
            }
        }
        throw com.example.pogun.dto.common.ApiResponse.ApiException.unauthorized("TOKEN_REFRESH_REQUIRED", "리프레시 토큰이 필요합니다.");
    }

    @PostMapping("/onboarding/complete")
    @Operation(summary = "온보딩 완료", description = "소셜 인증 후 지역 정보를 받아 정식 회원가입을 완료합니다.")
    public ResponseEntity<ApiResponse<AuthResponse>> completeOnboarding(@Valid @RequestBody OnboardingCompleteRequest request) {
        AuthResponse data = authService.completeOnboarding(request);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "회원가입 완료", data));
    }

    @GetMapping("/onboarding/map-config")
    @Operation(summary = "온보딩 지도 설정 조회", description = "온보딩 위치 선택 화면에서 사용할 카카오 키 설정 상태를 조회합니다.")
    public ResponseEntity<ApiResponse<OnboardingMapConfigResponse>> getOnboardingMapConfig() {
        OnboardingMapConfigResponse data = new OnboardingMapConfigResponse(
                StringUtils.hasText(kakaoLocalProperties.getRestApiKey()),
                StringUtils.hasText(kakaoLocalProperties.getJavascriptKey()),
                kakaoLocalProperties.getJavascriptKey()
        );
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "온보딩 지도 설정 조회 성공", data));
    }

    @PostMapping("/logout")
    @Operation(summary = "로그아웃", description = "사용자를 로그아웃 처리합니다.")
    public ResponseEntity<ApiResponse<LogoutResponse>> logout(HttpServletResponse response) {
        LogoutResponse data = authService.logout();
        ResponseCookie expiredCookie = ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(false)
                .sameSite("Strict")
                .path("/api/auth")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, expiredCookie.toString());
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
