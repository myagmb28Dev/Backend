package com.example.pogun.service.payment;

import com.apple.itunes.storekit.model.Data;
import com.apple.itunes.storekit.model.JWSTransactionDecodedPayload;
import com.apple.itunes.storekit.model.NotificationTypeV2;
import com.apple.itunes.storekit.model.ResponseBodyV2DecodedPayload;
import com.example.pogun.config.payment.PaymentProperties;
import com.example.pogun.dto.payment.AppleAppStoreNotificationRequest;
import com.example.pogun.dto.payment.AppleAppStoreNotificationResponse;
import com.example.pogun.entity.payment.AppleAppStoreNotification;
import com.example.pogun.entity.payment.Purchase;
import com.example.pogun.entity.payment.enums.PaymentPlatform;
import com.example.pogun.entity.payment.enums.PurchaseStatus;
import com.example.pogun.entity.user.User;
import com.example.pogun.entity.user.enums.UserRole;
import com.example.pogun.entity.user.enums.UserStatus;
import com.example.pogun.repository.payment.AppleAppStoreNotificationRepository;
import com.example.pogun.repository.payment.PurchaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppleAppStoreNotificationServiceTest {

    @Mock
    private AppleStoreKitClient appleStoreKitClient;
    @Mock
    private AppleAppStoreNotificationRepository notificationRepository;
    @Mock
    private PurchaseRepository purchaseRepository;
    @Mock
    private CreditService creditService;

    private PaymentProperties properties;
    private AppleAppStoreNotificationService service;

    @BeforeEach
    void setUp() {
        properties = new PaymentProperties();
        properties.getApple().setEnabled(true);
        properties.getApple().setBundleId("com.example.pogun");
        properties.getApple().setEnvironment("sandbox");
        service = new AppleAppStoreNotificationService(
                properties,
                appleStoreKitClient,
                notificationRepository,
                purchaseRepository,
                creditService
        );
    }

    @Test
    void handle_marksPurchaseRefundedAndRevokesUnusedCredits() {
        User user = user();
        Purchase purchase = Purchase.builder()
                .id(UUID.randomUUID())
                .user(user)
                .platform(PaymentPlatform.IOS)
                .status(PurchaseStatus.VERIFIED)
                .productId("paw_ai_credits_3_300")
                .transactionId("tx-1")
                .originalTransactionId("orig-1")
                .creditedCredits(3)
                .consumedCredits(1)
                .build();
        ResponseBodyV2DecodedPayload notificationPayload = notificationPayload(NotificationTypeV2.REFUND);
        JWSTransactionDecodedPayload transactionPayload = transactionPayload();
        AppleAppStoreNotificationRequest request = new AppleAppStoreNotificationRequest();
        request.setSignedPayload("signed-notification");

        when(appleStoreKitClient.verifyAndDecodeNotification(properties.getApple(), "signed-notification"))
                .thenReturn(notificationPayload);
        when(notificationRepository.findByNotificationUuid("notification-uuid")).thenReturn(Optional.empty());
        when(appleStoreKitClient.verifyAndDecodeTransaction(properties.getApple(), "signed-transaction"))
                .thenReturn(transactionPayload);
        when(purchaseRepository.findByTransactionId("tx-1")).thenReturn(Optional.of(purchase));
        when(creditService.revokeUnusedCreditsForPurchase(user, purchase, "paw_ai_credits_3_300 Apple REFUND 미사용 크레딧 회수"))
                .thenReturn(2);
        when(notificationRepository.saveAndFlush(any(AppleAppStoreNotification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AppleAppStoreNotificationResponse response = service.handle(request);

        assertThat(response.notificationUuid()).isEqualTo("notification-uuid");
        assertThat(response.notificationType()).isEqualTo("REFUND");
        assertThat(response.processingResult()).isEqualTo("REFUNDED_UNUSED_CREDITS_2");
        assertThat(response.purchaseId()).isEqualTo(purchase.getId());
        assertThat(purchase.getStatus()).isEqualTo(PurchaseStatus.REFUNDED);
        assertThat(purchase.getProviderRevokedAt()).isEqualTo(Instant.parse("2026-06-17T00:00:00Z"));
        verify(purchaseRepository).save(purchase);
    }

    @Test
    void handle_returnsExistingNotificationResultWithoutReprocessing() {
        AppleAppStoreNotification existing = AppleAppStoreNotification.builder()
                .notificationUuid("notification-uuid")
                .notificationType("TEST")
                .processingResult("RECORDED")
                .processedAt(Instant.now())
                .build();
        AppleAppStoreNotificationRequest request = new AppleAppStoreNotificationRequest();
        request.setSignedPayload("signed-notification");
        ResponseBodyV2DecodedPayload notificationPayload = notificationPayload(NotificationTypeV2.TEST);

        when(appleStoreKitClient.verifyAndDecodeNotification(properties.getApple(), "signed-notification"))
                .thenReturn(notificationPayload);
        when(notificationRepository.findByNotificationUuid("notification-uuid")).thenReturn(Optional.of(existing));

        AppleAppStoreNotificationResponse response = service.handle(request);

        assertThat(response.processingResult()).isEqualTo("RECORDED");
    }

    private ResponseBodyV2DecodedPayload notificationPayload(NotificationTypeV2 notificationType) {
        ResponseBodyV2DecodedPayload payload = new ResponseBodyV2DecodedPayload();
        payload.setNotificationType(notificationType);
        payload.setNotificationUUID("notification-uuid");
        Data data = new Data();
        data.setSignedTransactionInfo("signed-transaction");
        payload.setData(data);
        return payload;
    }

    private JWSTransactionDecodedPayload transactionPayload() {
        JWSTransactionDecodedPayload payload = new JWSTransactionDecodedPayload();
        payload.setTransactionId("tx-1");
        payload.setOriginalTransactionId("orig-1");
        payload.setRevocationDate(Instant.parse("2026-06-17T00:00:00Z").toEpochMilli());
        return payload;
    }

    private User user() {
        return User.builder()
                .id(UUID.randomUUID())
                .firebaseUid("firebase-uid")
                .email("user@example.com")
                .nickname("tester")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
    }
}
