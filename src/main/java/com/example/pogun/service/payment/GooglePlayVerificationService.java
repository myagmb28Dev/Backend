package com.example.pogun.service.payment;

import com.example.pogun.config.payment.PaymentProperties;
import com.example.pogun.dto.common.ApiResponse.ApiException;
import com.example.pogun.dto.payment.PaymentVerifyRequest;
import com.example.pogun.entity.payment.enums.PaymentPlatform;
import org.springframework.stereotype.Service;

@Service
public class GooglePlayVerificationService implements PlatformPurchaseVerifier {

    private final PaymentProperties paymentProperties;

    public GooglePlayVerificationService(PaymentProperties paymentProperties) {
        this.paymentProperties = paymentProperties;
    }

    @Override
    public PaymentPlatform supports() {
        return PaymentPlatform.ANDROID;
    }

    @Override
    public VerifiedPurchase verify(PaymentVerifyRequest request) {
        if (!paymentProperties.getGoogle().isEnabled()
                || paymentProperties.getGoogle().getServiceAccountBase64() == null
                || paymentProperties.getGoogle().getServiceAccountBase64().isBlank()) {
            throw ApiException.conflict("GOOGLE_PLAY_NOT_CONFIGURED", "Google Play Developer API 자격 증명이 아직 구성되지 않았습니다.");
        }
        throw ApiException.conflict("GOOGLE_PLAY_NOT_IMPLEMENTED", "Google Play 검증은 서비스 계정 구성 이후 구현 예정입니다.");
    }
}
