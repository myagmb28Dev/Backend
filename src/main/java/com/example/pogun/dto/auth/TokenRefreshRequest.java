package com.example.pogun.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Firebase 토큰 리프레시 요청")
public class TokenRefreshRequest {

    @Size(max = 4096, message = "refreshToken 길이가 너무 깁니다.")
    @Schema(description = "Firebase Refresh Token", example = "AEu4IL1...")
    private String refreshToken;
}
