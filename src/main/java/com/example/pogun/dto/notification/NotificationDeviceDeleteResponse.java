package com.example.pogun.dto.notification;

import java.util.UUID;

public record NotificationDeviceDeleteResponse(
        UUID id,
        String platform,
        String deviceId,
        int deletedCount
) {
}
