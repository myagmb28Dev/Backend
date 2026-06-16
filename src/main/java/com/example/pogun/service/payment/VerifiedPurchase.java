package com.example.pogun.service.payment;

import com.example.pogun.entity.payment.enums.PaymentPlatform;

import java.time.Instant;

public record VerifiedPurchase(
        PaymentPlatform platform,
        String productId,
        String transactionId,
        String originalTransactionId,
        String purchaseToken,
        String environment,
        Instant purchasedAt,
        String rawPayload
) {
}
