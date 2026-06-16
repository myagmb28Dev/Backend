package com.example.pogun.dto.payment;

import java.time.Instant;
import java.util.UUID;

public record PaymentVerifyResponse(
        UUID purchaseId,
        String platform,
        String productId,
        String transactionId,
        String purchaseToken,
        Integer creditedCredits,
        Integer balanceAfter,
        Instant processedAt
) {
}
