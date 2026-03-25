package com.example.pogun.dto.notification;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
/**
 * API 요청/응답 데이터 전송 객체인 NotificationResponse이다.
 */

@Schema(description = "알림 응답")
public record NotificationResponse(
        UUID id,
        String type,
        String targetType,
        UUID targetId,
        String title,
        String body,
        Boolean isRead,
        Instant createdAt
) {
}
