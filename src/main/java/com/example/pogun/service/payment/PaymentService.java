package com.example.pogun.service.payment;

import com.example.pogun.config.payment.PaymentProperties;
import com.example.pogun.dto.common.ApiResponse.ApiException;
import com.example.pogun.dto.payment.PaymentVerifyRequest;
import com.example.pogun.dto.payment.PaymentVerifyResponse;
import com.example.pogun.entity.payment.Purchase;
import com.example.pogun.entity.payment.enums.PaymentPlatform;
import com.example.pogun.entity.payment.enums.PurchaseStatus;
import com.example.pogun.entity.user.User;
import com.example.pogun.repository.payment.PurchaseRepository;
import com.example.pogun.service.user.CurrentUserService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final List<PlatformPurchaseVerifier> verifiers;
    private final PurchaseRepository purchaseRepository;
    private final CreditService creditService;
    private final CurrentUserService currentUserService;
    private final PaymentProperties paymentProperties;
    private final ObjectMapper objectMapper;

    @Transactional
    public PaymentVerifyResponse verify(PaymentVerifyRequest request) {
        User user = currentUserService.getCurrentUser();
        PaymentPlatform platform = parsePlatform(request.getPlatform());
        PaymentProductCatalog catalog = PaymentProductCatalog.requireSupported(request.getProductId());

        Purchase existingPurchase = findExistingPurchase(request);
        if (existingPurchase != null) {
            if (!existingPurchase.getUser().getId().equals(user.getId())) {
                throw ApiException.conflict("PURCHASE_ALREADY_USED", "이미 다른 사용자에게 처리된 결제입니다.");
            }
            return new PaymentVerifyResponse(
                    existingPurchase.getId(),
                    existingPurchase.getPlatform().name(),
                    existingPurchase.getProductId(),
                    existingPurchase.getTransactionId(),
                    existingPurchase.getPurchaseToken(),
                    existingPurchase.getCreditedCredits(),
                    creditService.getCurrentBalance().balance(),
                    existingPurchase.getCreatedAt()
            );
        }

        VerifiedPurchase verified = verifierMap().get(platform).verify(request);
        if (!catalog.getProductId().equals(verified.productId())) {
            throw ApiException.badRequest("PRODUCT_ID_MISMATCH", "검증된 상품 ID가 요청과 일치하지 않습니다.");
        }
        validateAccountBinding(user, verified);
        ensureNotAlreadyProcessed(verified);

        Purchase purchase;
        try {
            purchase = purchaseRepository.saveAndFlush(Purchase.builder()
                    .user(user)
                    .platform(platform)
                    .status(PurchaseStatus.VERIFIED)
                    .productId(catalog.getProductId())
                    .transactionId(verified.transactionId())
                    .originalTransactionId(verified.originalTransactionId())
                    .appAccountToken(verified.appAccountToken() == null ? null : verified.appAccountToken().toString())
                    .purchaseToken(verified.purchaseToken())
                    .creditedCredits(catalog.getCredits())
                    .consumedCredits(0)
                    .providerPurchaseAt(verified.purchasedAt())
                    .providerEnvironment(verified.environment())
                    .verificationPayload(verified.rawPayload())
                    .build());
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("DUPLICATE_PURCHASE", "이미 처리 중이거나 처리된 결제입니다. 잠시 후 잔액을 다시 조회해주세요.");
        }

        int balanceAfter = creditService.addCreditsForPurchase(user, purchase, catalog.getCredits());
        return new PaymentVerifyResponse(
                purchase.getId(),
                purchase.getPlatform().name(),
                purchase.getProductId(),
                purchase.getTransactionId(),
                purchase.getPurchaseToken(),
                purchase.getCreditedCredits(),
                balanceAfter,
                purchase.getCreatedAt()
        );
    }

    private Purchase findExistingPurchase(PaymentVerifyRequest request) {
        String transactionId = firstNonBlank(request.getTransactionId(), transactionIdFromSignedTransactionInfo(request.getSignedTransactionInfo()));
        if (transactionId != null) {
            return purchaseRepository.findByTransactionId(transactionId).orElse(null);
        }
        if (request.getPurchaseToken() != null && !request.getPurchaseToken().isBlank()) {
            return purchaseRepository.findByPurchaseToken(request.getPurchaseToken().trim()).orElse(null);
        }
        return null;
    }

    private void validateAccountBinding(User user, VerifiedPurchase verified) {
        if (verified.platform() != PaymentPlatform.IOS) {
            return;
        }

        UUID appAccountToken = verified.appAccountToken();
        if (appAccountToken == null) {
            if (paymentProperties.getApple().isAppAccountTokenRequired()) {
                throw ApiException.badRequest("APPLE_APP_ACCOUNT_TOKEN_REQUIRED", "Apple 거래의 appAccountToken이 필요합니다.");
            }
            return;
        }

        if (!appAccountToken.equals(user.getId())) {
            throw ApiException.conflict("APPLE_APP_ACCOUNT_TOKEN_MISMATCH", "Apple 거래 appAccountToken이 현재 사용자와 일치하지 않습니다.");
        }
    }

    private void ensureNotAlreadyProcessed(VerifiedPurchase verified) {
        if (verified.transactionId() != null && purchaseRepository.findByTransactionId(verified.transactionId()).isPresent()) {
            throw ApiException.conflict("DUPLICATE_TRANSACTION_ID", "이미 처리된 transactionId 입니다.");
        }
        if (verified.purchaseToken() != null && purchaseRepository.findByPurchaseToken(verified.purchaseToken()).isPresent()) {
            throw ApiException.conflict("DUPLICATE_PURCHASE_TOKEN", "이미 처리된 purchaseToken 입니다.");
        }
    }

    private PaymentPlatform parsePlatform(String platform) {
        try {
            return PaymentPlatform.valueOf(platform.trim().toUpperCase());
        } catch (Exception e) {
            throw ApiException.badRequest("INVALID_PAYMENT_PLATFORM", "지원하지 않는 결제 플랫폼입니다.");
        }
    }

    private Map<PaymentPlatform, PlatformPurchaseVerifier> verifierMap() {
        Map<PaymentPlatform, PlatformPurchaseVerifier> map = new EnumMap<>(PaymentPlatform.class);
        for (PlatformPurchaseVerifier verifier : verifiers) {
            map.put(verifier.supports(), verifier);
        }
        if (!map.containsKey(PaymentPlatform.IOS) || !map.containsKey(PaymentPlatform.ANDROID)) {
            throw ApiException.internal("PAYMENT_VERIFIER_NOT_READY", "결제 검증 서비스 구성이 완전하지 않습니다.");
        }
        return map;
    }

    private String transactionIdFromSignedTransactionInfo(String signedTransactionInfo) {
        if (signedTransactionInfo == null || signedTransactionInfo.isBlank()) {
            return null;
        }
        try {
            String[] parts = signedTransactionInfo.split("\\.");
            if (parts.length < 2) {
                return null;
            }
            byte[] decoded = Base64.getUrlDecoder().decode(padBase64(parts[1]));
            Map<String, Object> payload = objectMapper.readValue(decoded, new TypeReference<>() {
            });
            Object transactionId = payload.get("transactionId");
            return transactionId == null || String.valueOf(transactionId).isBlank()
                    ? null
                    : String.valueOf(transactionId).trim();
        } catch (Exception e) {
            return null;
        }
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        if (second != null && !second.isBlank()) {
            return second.trim();
        }
        return null;
    }

    private String padBase64(String value) {
        int remainder = value.length() % 4;
        if (remainder == 0) {
            return value;
        }
        return value + "=".repeat(4 - remainder);
    }
}
