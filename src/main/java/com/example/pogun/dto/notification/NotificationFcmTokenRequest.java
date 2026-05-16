package com.example.pogun.dto.notification;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * API 요청/응답 데이터 전송 객체인 NotificationFcmTokenRequest이다.
 */

@Getter
@Setter
@Schema(description = "FCM 토큰 갱신 요청")
public class NotificationFcmTokenRequest {
    @NotBlank
    @Schema(description = "FCM 토큰", example = "fcm-token-value")
    private String token;

    @Schema(description = "플랫폼", example = "ANDROID")
    private String platform;

    @Schema(description = "디바이스 ID", example = "pixel-1")
    private String deviceId;
}
