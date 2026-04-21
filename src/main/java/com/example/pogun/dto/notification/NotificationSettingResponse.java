package com.example.pogun.dto.notification;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "알림 설정 응답")
public record NotificationSettingResponse(String type, Boolean enabled) {
}
