package com.example.pogun.service.payment;

import com.example.pogun.dto.payment.PaymentVerifyRequest;
import com.example.pogun.entity.payment.enums.PaymentPlatform;

public interface PlatformPurchaseVerifier {
    PaymentPlatform supports();

    VerifiedPurchase verify(PaymentVerifyRequest request);
}
