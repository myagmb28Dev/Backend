package com.example.pogun.dto.payment;

import java.util.UUID;

public record AppleAppStoreNotificationResponse(
        String notificationUuid,
        String notificationType,
        String subtype,
        String processingResult,
        String transactionId,
        UUID purchaseId
) {
}
