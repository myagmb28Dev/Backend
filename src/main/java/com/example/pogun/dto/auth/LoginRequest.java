package com.example.pogun.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
/**
 * API 요청/응답 데이터 전송 객체인 LoginRequest이다.
 */

@Getter
@Setter
@Schema(description = "로그인 요청 정보")
public class LoginRequest {
    @NotBlank(message = "firebaseIdToken은 필수입니다.")
    @Schema(description = "Firebase ID Token", example = "eyJhbGciOiJSUzI1NiIsImtpZCI6IjIy...")
    private String firebaseIdToken;
}
