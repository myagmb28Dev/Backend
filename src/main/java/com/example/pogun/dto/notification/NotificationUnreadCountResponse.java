package com.example.pogun.dto.notification;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "읽지 않은 알림 수 응답")
public record NotificationUnreadCountResponse(long unreadCount) {
}
