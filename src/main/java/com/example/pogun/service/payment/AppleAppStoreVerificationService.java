package com.example.pogun.service.payment;

import com.apple.itunes.storekit.model.JWSTransactionDecodedPayload;
import com.example.pogun.config.payment.PaymentProperties;
import com.example.pogun.dto.common.ApiResponse.ApiException;
import com.example.pogun.dto.payment.PaymentVerifyRequest;
import com.example.pogun.entity.payment.enums.PaymentPlatform;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AppleAppStoreVerificationService implements PlatformPurchaseVerifier {

    private final PaymentProperties paymentProperties;
    private final AppleStoreKitClient appleStoreKitClient;

    @Override
    public PaymentPlatform supports() {
        return PaymentPlatform.IOS;
    }

    @Override
    public VerifiedPurchase verify(PaymentVerifyRequest request) {
        PaymentProperties.Apple apple = paymentProperties.getApple();
        validateAppleConfiguration(apple);

        String requestedTransactionId = resolveTransactionId(apple, request);
        String signedTransactionInfo = appleStoreKitClient.getSignedTransactionInfo(apple, requestedTransactionId);
        if (isBlank(signedTransactionInfo)) {
            throw ApiException.badRequest("APPLE_TRANSACTION_VERIFICATION_FAILED", "Apple 응답에서 거래 정보를 찾을 수 없습니다.");
        }

        JWSTransactionDecodedPayload payload = appleStoreKitClient.verifyAndDecodeTransaction(apple, signedTransactionInfo);
        String verifiedTransactionId = payload.getTransactionId();
        if (isBlank(verifiedTransactionId) || !verifiedTransactionId.equals(requestedTransactionId)) {
            throw ApiException.badRequest("APPLE_TRANSACTION_ID_MISMATCH", "Apple 검증 응답의 transactionId가 요청과 일치하지 않습니다.");
        }
        validateTransactionPayload(payload);

        return new VerifiedPurchase(
                PaymentPlatform.IOS,
                payload.getProductId(),
                verifiedTransactionId,
                payload.getOriginalTransactionId(),
                payload.getAppAccountToken(),
                null,
                payload.getRawEnvironment(),
                toInstant(payload.getPurchaseDate()),
                signedTransactionInfo
        );
    }

    private String resolveTransactionId(PaymentProperties.Apple apple, PaymentVerifyRequest request) {
        if (!isBlank(request.getTransactionId())) {
            return request.getTransactionId().trim();
        }
        if (!isBlank(request.getSignedTransactionInfo())) {
            JWSTransactionDecodedPayload payload = appleStoreKitClient.verifyAndDecodeTransaction(apple, request.getSignedTransactionInfo());
            if (!isBlank(payload.getTransactionId())) {
                return payload.getTransactionId().trim();
            }
        }
        throw ApiException.badRequest("MISSING_TRANSACTION_ID", "iOS 결제 검증에는 transactionId 또는 signedTransactionInfo가 필요합니다.");
    }

    private void validateAppleConfiguration(PaymentProperties.Apple apple) {
        if (!apple.isEnabled()) {
            throw ApiException.conflict("APPLE_PAYMENT_DISABLED", "Apple 결제 검증이 비활성화되어 있습니다.");
        }
        if (isBlank(apple.getKeyId()) || isBlank(apple.getIssuerId()) || isBlank(apple.getBundleId()) || isBlank(apple.getPrivateKeyBase64())) {
            throw ApiException.internal("APPLE_PAYMENT_NOT_CONFIGURED", "Apple 결제 검증 설정이 누락되었습니다.");
        }
        if ("production".equalsIgnoreCase(apple.getEnvironment()) && apple.getAppAppleId() == null) {
            throw ApiException.internal("APPLE_PAYMENT_NOT_CONFIGURED", "운영 Apple 결제 검증에는 appAppleId 설정이 필요합니다.");
        }
    }

    private void validateTransactionPayload(JWSTransactionDecodedPayload payload) {
        if (payload.getRevocationDate() != null) {
            throw ApiException.conflict("APPLE_TRANSACTION_REVOKED", "환불 또는 취소된 Apple 거래입니다.");
        }

        Instant expiresAt = toInstant(payload.getExpiresDate());
        if (expiresAt != null && expiresAt.isBefore(Instant.now())) {
            throw ApiException.conflict("APPLE_TRANSACTION_EXPIRED", "만료된 Apple 거래입니다.");
        }

        String type = payload.getRawType();
        if (!isBlank(type) && !"consumable".equalsIgnoreCase(type)) {
            throw ApiException.badRequest("APPLE_PRODUCT_TYPE_MISMATCH", "소모성 인앱 결제 상품만 처리할 수 있습니다.");
        }
    }

    private Instant toInstant(Long millis) {
        return millis == null ? null : Instant.ofEpochMilli(millis);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
