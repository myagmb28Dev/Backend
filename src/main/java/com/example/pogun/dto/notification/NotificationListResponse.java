package com.example.pogun.dto.notification;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "알림 목록 응답")
public record NotificationListResponse(
        long totalElements,
        int totalPages,
        int page,
        int size,
        List<NotificationResponse> items
) {
}
