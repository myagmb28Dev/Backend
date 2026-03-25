package com.example.pogun.dto.notification;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
/**
 * API 요청/응답 데이터 전송 객체인 NotificationFcmTokenResponse이다.
 */

@Schema(description = "FCM 토큰 등록 응답")
public record NotificationFcmTokenResponse(UUID id, String token, String platform, String deviceId, Boolean active) {
}