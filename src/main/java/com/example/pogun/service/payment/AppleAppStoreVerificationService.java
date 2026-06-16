package com.example.pogun.service.payment;

import com.example.pogun.config.payment.PaymentProperties;
import com.example.pogun.dto.common.ApiResponse.ApiException;
import com.example.pogun.dto.payment.PaymentVerifyRequest;
import com.example.pogun.entity.payment.enums.PaymentPlatform;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AppleAppStoreVerificationService implements PlatformPurchaseVerifier {

    private final PaymentProperties paymentProperties;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    public AppleAppStoreVerificationService(
            PaymentProperties paymentProperties,
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper
    ) {
        this.paymentProperties = paymentProperties;
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = objectMapper;
    }

    @Override
    public PaymentPlatform supports() {
        return PaymentPlatform.IOS;
    }

    @Override
    public VerifiedPurchase verify(PaymentVerifyRequest request) {
        PaymentProperties.Apple apple = paymentProperties.getApple();
        validateAppleConfiguration(apple);

        String transactionId = resolveTransactionId(request);
        Map<String, Object> responseBody = webClientBuilder.build()
                .get()
                .uri(resolveBaseUrl(apple) + "/inApps/v1/transactions/" + transactionId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + createSignedJwt(apple))
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .map(body -> ApiException.badRequest("APPLE_TRANSACTION_VERIFICATION_FAILED", body)))
                .bodyToMono(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {
                })
                .block();

        if (responseBody == null || responseBody.get("signedTransactionInfo") == null) {
            throw ApiException.badRequest("APPLE_TRANSACTION_VERIFICATION_FAILED", "Apple 응답에서 거래 정보를 찾을 수 없습니다.");
        }

        String signedTransactionInfo = String.valueOf(responseBody.get("signedTransactionInfo"));
        Map<String, Object> payload = decodeJwtPayload(signedTransactionInfo);
        String productId = stringValue(payload.get("productId"));
        String verifiedTransactionId = stringValue(payload.get("transactionId"));
        if (verifiedTransactionId == null || !verifiedTransactionId.equals(transactionId)) {
            throw ApiException.badRequest("APPLE_TRANSACTION_ID_MISMATCH", "Apple 검증 응답의 transactionId가 요청과 일치하지 않습니다.");
        }
        Instant purchasedAt = toInstant(payload.get("purchaseDate"));
        return new VerifiedPurchase(
                PaymentPlatform.IOS,
                productId,
                verifiedTransactionId,
                stringValue(payload.get("originalTransactionId")),
                null,
                stringValue(payload.getOrDefault("environment", apple.getEnvironment())),
                purchasedAt,
                signedTransactionInfo
        );
    }

    private void validateAppleConfiguration(PaymentProperties.Apple apple) {
        if (!apple.isEnabled()) {
            throw ApiException.conflict("APPLE_PAYMENT_DISABLED", "Apple 결제 검증이 비활성화되어 있습니다.");
        }
        if (isBlank(apple.getKeyId()) || isBlank(apple.getIssuerId()) || isBlank(apple.getBundleId()) || isBlank(apple.getPrivateKeyBase64())) {
            throw ApiException.internal("APPLE_PAYMENT_NOT_CONFIGURED", "Apple 결제 검증 설정이 누락되었습니다.");
        }
    }

    private String resolveTransactionId(PaymentVerifyRequest request) {
        if (!isBlank(request.getTransactionId())) {
            return request.getTransactionId().trim();
        }
        if (!isBlank(request.getSignedTransactionInfo())) {
            Map<String, Object> payload = decodeJwtPayload(request.getSignedTransactionInfo());
            String transactionId = stringValue(payload.get("transactionId"));
            if (!isBlank(transactionId)) {
                return transactionId.trim();
            }
        }
        throw ApiException.badRequest("MISSING_TRANSACTION_ID", "iOS 결제 검증에는 transactionId 또는 signedTransactionInfo가 필요합니다.");
    }

    private String resolveBaseUrl(PaymentProperties.Apple apple) {
        return "production".equalsIgnoreCase(apple.getEnvironment())
                ? "https://api.storekit.itunes.apple.com"
                : "https://api.storekit-sandbox.itunes.apple.com";
    }

    private String createSignedJwt(PaymentProperties.Apple apple) {
        try {
            long now = Instant.now().getEpochSecond();
            Map<String, Object> header = new LinkedHashMap<>();
            header.put("alg", "ES256");
            header.put("kid", apple.getKeyId());
            header.put("typ", "JWT");

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("iss", apple.getIssuerId());
            payload.put("iat", now);
            payload.put("exp", now + 300);
            payload.put("aud", "appstoreconnect-v1");
            payload.put("bid", apple.getBundleId());

            String headerEncoded = base64Url(objectMapper.writeValueAsBytes(header));
            String payloadEncoded = base64Url(objectMapper.writeValueAsBytes(payload));
            String signingInput = headerEncoded + "." + payloadEncoded;

            Signature signature = Signature.getInstance("SHA256withECDSA");
            signature.initSign(loadPrivateKey(apple.getPrivateKeyBase64()));
            signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
            String signatureEncoded = base64Url(signature.sign());
            return signingInput + "." + signatureEncoded;
        } catch (Exception e) {
            throw ApiException.internal("APPLE_JWT_SIGNING_FAILED", "Apple App Store JWT 생성에 실패했습니다.");
        }
    }

    private PrivateKey loadPrivateKey(String privateKeyBase64) throws Exception {
        byte[] pemBytes = Base64.getDecoder().decode(privateKeyBase64);
        String pem = new String(pemBytes, StandardCharsets.UTF_8)
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] keyBytes = Base64.getDecoder().decode(pem);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        return KeyFactory.getInstance("EC").generatePrivate(keySpec);
    }

    private Map<String, Object> decodeJwtPayload(String jws) {
        try {
            String[] parts = jws.split("\\.");
            if (parts.length < 2) {
                throw ApiException.badRequest("INVALID_SIGNED_TRANSACTION_INFO", "signedTransactionInfo 형식이 올바르지 않습니다.");
            }
            byte[] decoded = Base64.getUrlDecoder().decode(padBase64(parts[1]));
            return objectMapper.readValue(decoded, new TypeReference<>() {
            });
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw ApiException.badRequest("INVALID_SIGNED_TRANSACTION_INFO", "signedTransactionInfo를 해석할 수 없습니다.");
        }
    }

    private String padBase64(String value) {
        int remainder = value.length() % 4;
        if (remainder == 0) {
            return value;
        }
        return value + "=".repeat(4 - remainder);
    }

    private String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Instant toInstant(Object value) {
        if (value == null) {
            return null;
        }
        try {
            long millis = Long.parseLong(String.valueOf(value));
            return Instant.ofEpochMilli(millis);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
