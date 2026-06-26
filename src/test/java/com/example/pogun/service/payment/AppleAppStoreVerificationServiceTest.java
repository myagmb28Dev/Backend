package com.example.pogun.service.payment;

import com.apple.itunes.storekit.model.JWSTransactionDecodedPayload;
import com.example.pogun.config.payment.PaymentProperties;
import com.example.pogun.dto.common.ApiResponse.ApiException;
import com.example.pogun.dto.payment.PaymentVerifyRequest;
import com.example.pogun.entity.payment.enums.PaymentPlatform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppleAppStoreVerificationServiceTest {

    @Mock
    private AppleStoreKitClient appleStoreKitClient;

    private PaymentProperties properties;
    private AppleAppStoreVerificationService service;

    @BeforeEach
    void setUp() {
        properties = new PaymentProperties();
        properties.getApple().setEnabled(true);
        properties.getApple().setKeyId("KEY1234567");
        properties.getApple().setIssuerId("issuer-id");
        properties.getApple().setBundleId("com.example.pogun");
        properties.getApple().setEnvironment("sandbox");
        properties.getApple().setPrivateKeyBase64(Base64.getEncoder().encodeToString("private-key".getBytes(StandardCharsets.UTF_8)));
        service = new AppleAppStoreVerificationService(properties, appleStoreKitClient);
    }

    @Test
    void verify_usesStoreKitClientAndReturnsAppAccountToken() {
        UUID appAccountToken = UUID.randomUUID();
        PaymentVerifyRequest request = new PaymentVerifyRequest();
        request.setPlatform("ios");
        request.setProductId("paw_ai_credits_3_300");
        request.setTransactionId("tx-test-1");
        JWSTransactionDecodedPayload payload = transactionPayload("tx-test-1", appAccountToken);

        when(appleStoreKitClient.getSignedTransactionInfo(properties.getApple(), "tx-test-1"))
                .thenReturn("server-signed-transaction");
        when(appleStoreKitClient.verifyAndDecodeTransaction(properties.getApple(), "server-signed-transaction"))
                .thenReturn(payload);

        VerifiedPurchase verifiedPurchase = service.verify(request);

        assertThat(verifiedPurchase.platform()).isEqualTo(PaymentPlatform.IOS);
        assertThat(verifiedPurchase.productId()).isEqualTo("paw_ai_credits_3_300");
        assertThat(verifiedPurchase.transactionId()).isEqualTo("tx-test-1");
        assertThat(verifiedPurchase.originalTransactionId()).isEqualTo("orig-test-1");
        assertThat(verifiedPurchase.appAccountToken()).isEqualTo(appAccountToken);
        assertThat(verifiedPurchase.environment()).isEqualTo("Sandbox");
        assertThat(verifiedPurchase.purchasedAt()).isEqualTo(Instant.parse("2026-06-16T01:02:03Z"));
        assertThat(verifiedPurchase.rawPayload()).isEqualTo("server-signed-transaction");
    }

    @Test
    void verify_resolvesTransactionIdFromSignedTransactionInfo() {
        PaymentVerifyRequest request = new PaymentVerifyRequest();
        request.setPlatform("ios");
        request.setProductId("paw_ai_credits_3_300");
        request.setSignedTransactionInfo("client-signed-transaction");
        JWSTransactionDecodedPayload clientPayload = transactionPayload("tx-test-1", UUID.randomUUID());
        JWSTransactionDecodedPayload serverPayload = transactionPayload("tx-test-1", clientPayload.getAppAccountToken());

        when(appleStoreKitClient.verifyAndDecodeTransaction(properties.getApple(), "client-signed-transaction"))
                .thenReturn(clientPayload);
        when(appleStoreKitClient.getSignedTransactionInfo(properties.getApple(), "tx-test-1"))
                .thenReturn("server-signed-transaction");
        when(appleStoreKitClient.verifyAndDecodeTransaction(properties.getApple(), "server-signed-transaction"))
                .thenReturn(serverPayload);

        VerifiedPurchase verifiedPurchase = service.verify(request);

        assertThat(verifiedPurchase.transactionId()).isEqualTo("tx-test-1");
        verify(appleStoreKitClient).getSignedTransactionInfo(properties.getApple(), "tx-test-1");
    }

    @Test
    void verify_rejectsRevokedTransaction() {
        PaymentVerifyRequest request = new PaymentVerifyRequest();
        request.setPlatform("ios");
        request.setProductId("paw_ai_credits_3_300");
        request.setTransactionId("tx-test-1");
        JWSTransactionDecodedPayload payload = transactionPayload("tx-test-1", UUID.randomUUID());
        payload.setRevocationDate(Instant.parse("2026-06-17T00:00:00Z").toEpochMilli());

        when(appleStoreKitClient.getSignedTransactionInfo(properties.getApple(), "tx-test-1"))
                .thenReturn("server-signed-transaction");
        when(appleStoreKitClient.verifyAndDecodeTransaction(properties.getApple(), "server-signed-transaction"))
                .thenReturn(payload);

        assertThatThrownBy(() -> service.verify(request))
                .isInstanceOf(ApiException.class)
                .hasMessage("환불 또는 취소된 Apple 거래입니다.");
    }

    @Test
    void verify_requiresAppAppleIdInProduction() {
        properties.getApple().setEnvironment("production");
        PaymentVerifyRequest request = new PaymentVerifyRequest();
        request.setPlatform("ios");
        request.setProductId("paw_ai_credits_3_300");
        request.setTransactionId("tx-test-1");

        assertThatThrownBy(() -> service.verify(request))
                .isInstanceOf(ApiException.class)
                .hasMessage("운영 Apple 결제 검증에는 appAppleId 설정이 필요합니다.");
    }

    private JWSTransactionDecodedPayload transactionPayload(String transactionId, UUID appAccountToken) {
        JWSTransactionDecodedPayload payload = new JWSTransactionDecodedPayload();
        payload.setTransactionId(transactionId);
        payload.setOriginalTransactionId("orig-test-1");
        payload.setProductId("paw_ai_credits_3_300");
        payload.setAppAccountToken(appAccountToken);
        payload.setRawEnvironment("Sandbox");
        payload.setRawType("Consumable");
        payload.setPurchaseDate(Instant.parse("2026-06-16T01:02:03Z").toEpochMilli());
        return payload;
    }
}
