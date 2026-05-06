package com.example.pogun.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Firebase 토큰 리프레시 요청")
public class TokenRefreshRequest {

    @NotBlank(message = "refreshToken은 필수입니다.")
    @Schema(description = "Firebase Refresh Token", example = "AEu4IL1...")
    private String refreshToken;
}

