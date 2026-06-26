package com.example.pogun.service.payment;

import com.example.pogun.config.payment.PaymentProperties;
import com.example.pogun.dto.common.ApiResponse.ApiException;
import com.example.pogun.dto.payment.PaymentVerifyRequest;
import com.example.pogun.dto.payment.PaymentVerifyResponse;
import com.example.pogun.entity.payment.Purchase;
import com.example.pogun.entity.payment.enums.PaymentPlatform;
import com.example.pogun.entity.payment.enums.PurchaseStatus;
import com.example.pogun.entity.user.User;
import com.example.pogun.entity.user.enums.UserRole;
import com.example.pogun.entity.user.enums.UserStatus;
import com.example.pogun.repository.payment.PurchaseRepository;
import com.example.pogun.service.user.CurrentUserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PlatformPurchaseVerifier iosVerifier;
    @Mock
    private PlatformPurchaseVerifier androidVerifier;
    @Mock
    private PurchaseRepository purchaseRepository;
    @Mock
    private CreditService creditService;
    @Mock
    private CurrentUserService currentUserService;

    private PaymentService paymentService;
    private PaymentProperties paymentProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        paymentProperties = new PaymentProperties();
        paymentService = new PaymentService(
                List.of(iosVerifier, androidVerifier),
                purchaseRepository,
                creditService,
                currentUserService,
                paymentProperties,
                objectMapper
        );
    }

    @Test
    void verify_creditsUserWhenApplePurchaseIsValid() {
        User user = user("firebase-uid");
        PaymentVerifyRequest request = new PaymentVerifyRequest();
        request.setPlatform("ios");
        request.setProductId("paw_ai_credits_3_300");
        request.setTransactionId("tx-1");

        VerifiedPurchase verifiedPurchase = new VerifiedPurchase(
                PaymentPlatform.IOS,
                "paw_ai_credits_3_300",
                "tx-1",
                "orig-1",
                user.getId(),
                null,
                "SANDBOX",
                Instant.parse("2026-06-16T01:02:03Z"),
                "payload"
        );

        Purchase savedPurchase = Purchase.builder()
                .id(UUID.randomUUID())
                .user(user)
                .platform(PaymentPlatform.IOS)
                .status(PurchaseStatus.VERIFIED)
                .productId("paw_ai_credits_3_300")
                .transactionId("tx-1")
                .creditedCredits(3)
                .build();
        savedPurchase.setCreatedAt(Instant.parse("2026-06-16T01:02:04Z"));

        when(currentUserService.getCurrentUser()).thenReturn(user);
        when(iosVerifier.supports()).thenReturn(PaymentPlatform.IOS);
        when(androidVerifier.supports()).thenReturn(PaymentPlatform.ANDROID);
        when(purchaseRepository.findByTransactionId("tx-1")).thenReturn(Optional.empty());
        when(iosVerifier.verify(request)).thenReturn(verifiedPurchase);
        when(purchaseRepository.saveAndFlush(any(Purchase.class))).thenReturn(savedPurchase);
        when(creditService.addCreditsForPurchase(user, savedPurchase, 3)).thenReturn(3);

        PaymentVerifyResponse response = paymentService.verify(request);

        assertThat(response.productId()).isEqualTo("paw_ai_credits_3_300");
        assertThat(response.creditedCredits()).isEqualTo(3);
        assertThat(response.balanceAfter()).isEqualTo(3);
        verify(creditService).addCreditsForPurchase(user, savedPurchase, 3);
    }

    @Test
    void verify_returnsExistingResultWithoutDoubleCharge() {
        User user = user("firebase-uid");
        PaymentVerifyRequest request = new PaymentVerifyRequest();
        request.setPlatform("ios");
        request.setProductId("paw_ai_credits_3_300");
        request.setTransactionId("tx-1");

        Purchase existing = Purchase.builder()
                .id(UUID.randomUUID())
                .user(user)
                .platform(PaymentPlatform.IOS)
                .status(PurchaseStatus.VERIFIED)
                .productId("paw_ai_credits_3_300")
                .transactionId("tx-1")
                .creditedCredits(3)
                .build();
        existing.setCreatedAt(Instant.parse("2026-06-16T01:02:04Z"));

        when(currentUserService.getCurrentUser()).thenReturn(user);
        when(purchaseRepository.findByTransactionId("tx-1")).thenReturn(Optional.of(existing));
        when(creditService.getCurrentBalance()).thenReturn(new com.example.pogun.dto.user.UserCreditBalanceResponse(3, Instant.now()));

        PaymentVerifyResponse response = paymentService.verify(request);

        assertThat(response.purchaseId()).isEqualTo(existing.getId());
        verify(iosVerifier, never()).verify(any());
        verify(creditService, never()).addCreditsForPurchase(any(), any(), any(Integer.class));
    }

    @Test
    void verify_returnsExistingResultWhenOnlySignedTransactionInfoIsProvided() {
        User user = user("firebase-uid");
        PaymentVerifyRequest request = new PaymentVerifyRequest();
        request.setPlatform("ios");
        request.setProductId("paw_ai_credits_3_300");
        request.setSignedTransactionInfo(signedTransactionInfoWithTransactionId("tx-1"));

        Purchase existing = Purchase.builder()
                .id(UUID.randomUUID())
                .user(user)
                .platform(PaymentPlatform.IOS)
                .status(PurchaseStatus.VERIFIED)
                .productId("paw_ai_credits_3_300")
                .transactionId("tx-1")
                .creditedCredits(3)
                .build();
        existing.setCreatedAt(Instant.parse("2026-06-16T01:02:04Z"));

        when(currentUserService.getCurrentUser()).thenReturn(user);
        when(purchaseRepository.findByTransactionId("tx-1")).thenReturn(Optional.of(existing));
        when(creditService.getCurrentBalance()).thenReturn(new com.example.pogun.dto.user.UserCreditBalanceResponse(3, Instant.now()));

        PaymentVerifyResponse response = paymentService.verify(request);

        assertThat(response.purchaseId()).isEqualTo(existing.getId());
        verify(iosVerifier, never()).verify(any());
        verify(creditService, never()).addCreditsForPurchase(any(), any(), any(Integer.class));
    }

    @Test
    void verify_rejectsPurchaseAlreadyUsedByAnotherUser() {
        User user = user("firebase-uid");
        User anotherUser = user("firebase-uid-2");
        PaymentVerifyRequest request = new PaymentVerifyRequest();
        request.setPlatform("ios");
        request.setProductId("paw_ai_credits_3_300");
        request.setTransactionId("tx-1");

        Purchase existing = Purchase.builder()
                .id(UUID.randomUUID())
                .user(anotherUser)
                .platform(PaymentPlatform.IOS)
                .status(PurchaseStatus.VERIFIED)
                .productId("paw_ai_credits_3_300")
                .transactionId("tx-1")
                .creditedCredits(3)
                .build();

        when(currentUserService.getCurrentUser()).thenReturn(user);
        when(purchaseRepository.findByTransactionId("tx-1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> paymentService.verify(request))
                .isInstanceOf(ApiException.class)
                .hasMessage("이미 다른 사용자에게 처리된 결제입니다.");
    }

    @Test
    void verify_rejectsApplePurchaseWhenAppAccountTokenBelongsToAnotherUser() {
        User user = user("firebase-uid");
        PaymentVerifyRequest request = new PaymentVerifyRequest();
        request.setPlatform("ios");
        request.setProductId("paw_ai_credits_3_300");
        request.setTransactionId("tx-1");
        VerifiedPurchase verifiedPurchase = new VerifiedPurchase(
                PaymentPlatform.IOS,
                "paw_ai_credits_3_300",
                "tx-1",
                "orig-1",
                UUID.randomUUID(),
                null,
                "SANDBOX",
                Instant.parse("2026-06-16T01:02:03Z"),
                "payload"
        );

        when(currentUserService.getCurrentUser()).thenReturn(user);
        when(iosVerifier.supports()).thenReturn(PaymentPlatform.IOS);
        when(androidVerifier.supports()).thenReturn(PaymentPlatform.ANDROID);
        when(purchaseRepository.findByTransactionId("tx-1")).thenReturn(Optional.empty());
        when(iosVerifier.verify(request)).thenReturn(verifiedPurchase);

        assertThatThrownBy(() -> paymentService.verify(request))
                .isInstanceOf(ApiException.class)
                .hasMessage("Apple 거래 appAccountToken이 현재 사용자와 일치하지 않습니다.");
    }

    @Test
    void verify_requiresAppleAppAccountTokenWhenConfigured() {
        paymentProperties.getApple().setAppAccountTokenRequired(true);
        User user = user("firebase-uid");
        PaymentVerifyRequest request = new PaymentVerifyRequest();
        request.setPlatform("ios");
        request.setProductId("paw_ai_credits_3_300");
        request.setTransactionId("tx-1");
        VerifiedPurchase verifiedPurchase = new VerifiedPurchase(
                PaymentPlatform.IOS,
                "paw_ai_credits_3_300",
                "tx-1",
                "orig-1",
                null,
                null,
                "SANDBOX",
                Instant.parse("2026-06-16T01:02:03Z"),
                "payload"
        );

        when(currentUserService.getCurrentUser()).thenReturn(user);
        when(iosVerifier.supports()).thenReturn(PaymentPlatform.IOS);
        when(androidVerifier.supports()).thenReturn(PaymentPlatform.ANDROID);
        when(purchaseRepository.findByTransactionId("tx-1")).thenReturn(Optional.empty());
        when(iosVerifier.verify(request)).thenReturn(verifiedPurchase);

        assertThatThrownBy(() -> paymentService.verify(request))
                .isInstanceOf(ApiException.class)
                .hasMessage("Apple 거래의 appAccountToken이 필요합니다.");
    }

    private User user(String firebaseUid) {
        return User.builder()
                .id(UUID.randomUUID())
                .firebaseUid(firebaseUid)
                .email(firebaseUid + "@example.com")
                .nickname(firebaseUid)
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
    }

    private String signedTransactionInfoWithTransactionId(String transactionId) {
        String header = base64Url("{}");
        String payload = base64Url("{\"transactionId\":\"" + transactionId + "\"}");
        return header + "." + payload + ".signature";
    }

    private String base64Url(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
