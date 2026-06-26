package com.example.pogun.service.payment;

import com.apple.itunes.storekit.model.Data;
import com.apple.itunes.storekit.model.JWSTransactionDecodedPayload;
import com.apple.itunes.storekit.model.NotificationTypeV2;
import com.apple.itunes.storekit.model.ResponseBodyV2DecodedPayload;
import com.example.pogun.config.payment.PaymentProperties;
import com.example.pogun.dto.common.ApiResponse.ApiException;
import com.example.pogun.dto.payment.AppleAppStoreNotificationRequest;
import com.example.pogun.dto.payment.AppleAppStoreNotificationResponse;
import com.example.pogun.entity.payment.AppleAppStoreNotification;
import com.example.pogun.entity.payment.Purchase;
import com.example.pogun.entity.payment.enums.PurchaseStatus;
import com.example.pogun.repository.payment.AppleAppStoreNotificationRepository;
import com.example.pogun.repository.payment.PurchaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AppleAppStoreNotificationService {

    private final PaymentProperties paymentProperties;
    private final AppleStoreKitClient appleStoreKitClient;
    private final AppleAppStoreNotificationRepository notificationRepository;
    private final PurchaseRepository purchaseRepository;
    private final CreditService creditService;

    @Transactional
    public AppleAppStoreNotificationResponse handle(AppleAppStoreNotificationRequest request) {
        PaymentProperties.Apple apple = paymentProperties.getApple();
        validateAppleConfiguration(apple);

        ResponseBodyV2DecodedPayload payload = appleStoreKitClient.verifyAndDecodeNotification(apple, request.getSignedPayload());
        String notificationUuid = payload.getNotificationUUID();
        if (notificationUuid == null || notificationUuid.isBlank()) {
            throw ApiException.badRequest("APPLE_NOTIFICATION_UUID_MISSING", "Apple 서버 알림 UUID가 없습니다.");
        }

        AppleAppStoreNotification existing = notificationRepository.findByNotificationUuid(notificationUuid).orElse(null);
        if (existing != null) {
            return toResponse(existing, resolvePurchase(existing.getTransactionId(), existing.getOriginalTransactionId()));
        }

        JWSTransactionDecodedPayload transactionPayload = decodeTransactionPayload(apple, payload.getData());
        Purchase purchase = resolvePurchase(transactionPayload);
        String processingResult = processNotification(payload, transactionPayload, purchase);

        AppleAppStoreNotification notification = AppleAppStoreNotification.builder()
                .processedAt(Instant.now())
                .notificationUuid(notificationUuid)
                .notificationType(rawNotificationType(payload))
                .subtype(payload.getRawSubtype())
                .transactionId(transactionPayload == null ? null : transactionPayload.getTransactionId())
                .originalTransactionId(transactionPayload == null ? null : transactionPayload.getOriginalTransactionId())
                .processingResult(processingResult)
                .signedPayload(request.getSignedPayload())
                .build();

        try {
            notification = notificationRepository.saveAndFlush(notification);
        } catch (DataIntegrityViolationException e) {
            AppleAppStoreNotification concurrent = notificationRepository.findByNotificationUuid(notificationUuid)
                    .orElseThrow(() -> ApiException.conflict("APPLE_NOTIFICATION_DUPLICATE", "이미 처리 중인 Apple 서버 알림입니다."));
            return toResponse(concurrent, resolvePurchase(concurrent.getTransactionId(), concurrent.getOriginalTransactionId()));
        }

        return toResponse(notification, purchase);
    }

    private JWSTransactionDecodedPayload decodeTransactionPayload(PaymentProperties.Apple apple, Data data) {
        if (data == null || data.getSignedTransactionInfo() == null || data.getSignedTransactionInfo().isBlank()) {
            return null;
        }
        return appleStoreKitClient.verifyAndDecodeTransaction(apple, data.getSignedTransactionInfo());
    }

    private String processNotification(
            ResponseBodyV2DecodedPayload payload,
            JWSTransactionDecodedPayload transactionPayload,
            Purchase purchase
    ) {
        String notificationType = rawNotificationType(payload);
        if (!isRevocationNotification(notificationType)) {
            return "RECORDED";
        }
        if (purchase == null) {
            return "PURCHASE_NOT_FOUND";
        }
        if (purchase.getStatus() != PurchaseStatus.VERIFIED) {
            return "PURCHASE_ALREADY_" + purchase.getStatus().name();
        }

        PurchaseStatus nextStatus = "REVOKE".equals(notificationType) ? PurchaseStatus.REVOKED : PurchaseStatus.REFUNDED;
        purchase.setStatus(nextStatus);
        purchase.setProviderRevokedAt(toInstant(transactionPayload == null ? null : transactionPayload.getRevocationDate()));
        if (purchase.getProviderRevokedAt() == null) {
            purchase.setProviderRevokedAt(Instant.now());
        }
        purchase.setProviderRevocationReason(notificationType);
        int revokedCredits = creditService.revokeUnusedCreditsForPurchase(
                purchase.getUser(),
                purchase,
                purchase.getProductId() + " Apple " + notificationType + " 미사용 크레딧 회수"
        );
        purchaseRepository.save(purchase);
        return nextStatus.name() + "_UNUSED_CREDITS_" + revokedCredits;
    }

    private Purchase resolvePurchase(JWSTransactionDecodedPayload transactionPayload) {
        if (transactionPayload == null) {
            return null;
        }
        return resolvePurchase(transactionPayload.getTransactionId(), transactionPayload.getOriginalTransactionId());
    }

    private Purchase resolvePurchase(String transactionId, String originalTransactionId) {
        if (transactionId != null && !transactionId.isBlank()) {
            Purchase purchase = purchaseRepository.findByTransactionId(transactionId.trim()).orElse(null);
            if (purchase != null) {
                return purchase;
            }
        }
        if (originalTransactionId != null && !originalTransactionId.isBlank()) {
            return purchaseRepository.findByOriginalTransactionId(originalTransactionId.trim()).orElse(null);
        }
        return null;
    }

    private boolean isRevocationNotification(String notificationType) {
        return NotificationTypeV2.REFUND.getValue().equals(notificationType)
                || NotificationTypeV2.REVOKE.getValue().equals(notificationType);
    }

    private String rawNotificationType(ResponseBodyV2DecodedPayload payload) {
        String notificationType = payload.getRawNotificationType();
        if (notificationType == null || notificationType.isBlank()) {
            throw ApiException.badRequest("APPLE_NOTIFICATION_TYPE_MISSING", "Apple 서버 알림 유형이 없습니다.");
        }
        return notificationType;
    }

    private AppleAppStoreNotificationResponse toResponse(AppleAppStoreNotification notification, Purchase purchase) {
        return new AppleAppStoreNotificationResponse(
                notification.getNotificationUuid(),
                notification.getNotificationType(),
                notification.getSubtype(),
                notification.getProcessingResult(),
                notification.getTransactionId(),
                purchase == null ? null : purchase.getId()
        );
    }

    private void validateAppleConfiguration(PaymentProperties.Apple apple) {
        if (!apple.isEnabled()) {
            throw ApiException.conflict("APPLE_PAYMENT_DISABLED", "Apple 결제 검증이 비활성화되어 있습니다.");
        }
        if (isBlank(apple.getBundleId())) {
            throw ApiException.internal("APPLE_PAYMENT_NOT_CONFIGURED", "Apple 결제 검증 설정이 누락되었습니다.");
        }
        if ("production".equalsIgnoreCase(apple.getEnvironment()) && apple.getAppAppleId() == null) {
            throw ApiException.internal("APPLE_PAYMENT_NOT_CONFIGURED", "운영 Apple 서버 알림 검증에는 appAppleId 설정이 필요합니다.");
        }
    }

    private Instant toInstant(Long millis) {
        return millis == null ? null : Instant.ofEpochMilli(millis);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
