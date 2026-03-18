package com.example.demo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "로그인 요청 정보")
public class LoginRequest {
    @Schema(description = "소셜 제공자", example = "Google")
    private String provider;

    @Schema(description = "Firebase ID Token", example = "eyJhbGciOiJSUzI1NiIsImtpZCI6IjIy...")
    private String firebaseIdToken;
}
