package com.example.pogun.dto.notification;

import java.time.Instant;
import java.util.UUID;

public record NotificationDeviceResponse(
        UUID id,
        String platform,
        String deviceId,
        Boolean active,
        Instant lastSeenAt,
        Instant updatedAt
) {
}
