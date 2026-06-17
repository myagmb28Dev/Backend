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
import org.springframework.web.util.UriComponentsBuilder;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
                .uri(UriComponentsBuilder.fromUriString(resolveBaseUrl(apple))
                        .pathSegment("inApps", "v1", "transactions", transactionId)
                        .build()
                        .toUri())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + createSignedJwt(apple))
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .map(body -> ApiException.badRequest(
                                "APPLE_TRANSACTION_VERIFICATION_FAILED",
                                "Apple 거래 검증에 실패했습니다."
                        )))
                .bodyToMono(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {
                })
                .block();

        if (responseBody == null || responseBody.get("signedTransactionInfo") == null) {
            throw ApiException.badRequest("APPLE_TRANSACTION_VERIFICATION_FAILED", "Apple 응답에서 거래 정보를 찾을 수 없습니다.");
        }

        String signedTransactionInfo = String.valueOf(responseBody.get("signedTransactionInfo"));
        Map<String, Object> payload = verifyAndDecodeAppleSignedPayload(signedTransactionInfo);
        String productId = stringValue(payload.get("productId"));
        String verifiedTransactionId = stringValue(payload.get("transactionId"));
        if (verifiedTransactionId == null || !verifiedTransactionId.equals(transactionId)) {
            throw ApiException.badRequest("APPLE_TRANSACTION_ID_MISMATCH", "Apple 검증 응답의 transactionId가 요청과 일치하지 않습니다.");
        }
        validateTransactionPayload(apple, payload);

        Instant purchasedAt = toInstant(payload.get("purchaseDate"));
        return new VerifiedPurchase(
                PaymentPlatform.IOS,
                productId,
                verifiedTransactionId,
                stringValue(payload.get("originalTransactionId")),
                null,
                stringValue(payload.get("environment")),
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

    private Map<String, Object> verifyAndDecodeAppleSignedPayload(String jws) {
        String[] parts = jws.split("\\.");
        if (parts.length != 3) {
            throw ApiException.badRequest("INVALID_SIGNED_TRANSACTION_INFO", "signedTransactionInfo 형식이 올바르지 않습니다.");
        }
        Map<String, Object> header = decodeJwtPart(parts[0]);
        if (!"ES256".equals(stringValue(header.get("alg")))) {
            throw ApiException.badRequest("INVALID_SIGNED_TRANSACTION_INFO", "Apple 거래 서명 알고리즘이 올바르지 않습니다.");
        }
        List<X509Certificate> certificates = certificatesFromHeader(header);
        validateCertificateChain(certificates);
        verifyJwsSignature(parts, certificates.get(0));
        return decodeJwtPart(parts[1]);
    }

    private void validateTransactionPayload(PaymentProperties.Apple apple, Map<String, Object> payload) {
        String environment = stringValue(payload.get("environment"));
        if (isBlank(environment) || !normalize(environment).equals(normalize(apple.getEnvironment()))) {
            throw ApiException.badRequest("APPLE_ENVIRONMENT_MISMATCH", "Apple 거래 환경이 서버 설정과 일치하지 않습니다.");
        }

        String bundleId = stringValue(payload.get("bundleId"));
        if (isBlank(bundleId) || !bundleId.equals(apple.getBundleId())) {
            throw ApiException.badRequest("APPLE_BUNDLE_ID_MISMATCH", "Apple 거래 bundleId가 서버 설정과 일치하지 않습니다.");
        }

        if (payload.get("revocationDate") != null) {
            throw ApiException.conflict("APPLE_TRANSACTION_REVOKED", "환불 또는 취소된 Apple 거래입니다.");
        }

        Instant expiresAt = toInstant(payload.get("expiresDate"));
        if (expiresAt != null && expiresAt.isBefore(Instant.now())) {
            throw ApiException.conflict("APPLE_TRANSACTION_EXPIRED", "만료된 Apple 거래입니다.");
        }

        String type = stringValue(payload.get("type"));
        if (!isBlank(type) && !"consumable".equalsIgnoreCase(type)) {
            throw ApiException.badRequest("APPLE_PRODUCT_TYPE_MISMATCH", "소모성 인앱 결제 상품만 처리할 수 있습니다.");
        }
    }

    private List<X509Certificate> certificatesFromHeader(Map<String, Object> header) {
        Object x5cValue = header.get("x5c");
        if (!(x5cValue instanceof List<?> x5c) || x5c.isEmpty()) {
            throw ApiException.badRequest("INVALID_SIGNED_TRANSACTION_INFO", "Apple 거래 서명 인증서가 없습니다.");
        }
        try {
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            List<X509Certificate> certificates = new ArrayList<>();
            for (Object encodedCertificate : x5c) {
                byte[] certificateBytes = Base64.getDecoder().decode(String.valueOf(encodedCertificate));
                certificates.add((X509Certificate) certificateFactory.generateCertificate(new ByteArrayInputStream(certificateBytes)));
            }
            return certificates;
        } catch (Exception e) {
            throw ApiException.badRequest("INVALID_SIGNED_TRANSACTION_INFO", "Apple 거래 서명 인증서를 확인할 수 없습니다.");
        }
    }

    private void validateCertificateChain(List<X509Certificate> certificates) {
        try {
            certificates.get(0).checkValidity();
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            CertPath certPath = certificateFactory.generateCertPath(certificates);
            PKIXParameters parameters = new PKIXParameters(defaultTrustAnchors());
            parameters.setRevocationEnabled(false);
            CertPathValidator.getInstance("PKIX").validate(certPath, parameters);
        } catch (Exception e) {
            throw ApiException.badRequest("INVALID_SIGNED_TRANSACTION_INFO", "Apple 거래 서명 인증서 체인을 검증할 수 없습니다.");
        }
    }

    private Set<TrustAnchor> defaultTrustAnchors() throws Exception {
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init((KeyStore) null);
        Set<TrustAnchor> trustAnchors = new HashSet<>();
        for (TrustManager trustManager : trustManagerFactory.getTrustManagers()) {
            if (trustManager instanceof X509TrustManager x509TrustManager) {
                for (X509Certificate issuer : x509TrustManager.getAcceptedIssuers()) {
                    trustAnchors.add(new TrustAnchor(issuer, null));
                }
            }
        }
        return trustAnchors;
    }

    private void verifyJwsSignature(String[] parts, X509Certificate signingCertificate) {
        try {
            Signature verifier = Signature.getInstance("SHA256withECDSA");
            verifier.initVerify(signingCertificate.getPublicKey());
            verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8));
            byte[] rawSignature = Base64.getUrlDecoder().decode(padBase64(parts[2]));
            if (!verifier.verify(rawEcdsaSignatureToDer(rawSignature))) {
                throw ApiException.badRequest("INVALID_SIGNED_TRANSACTION_INFO", "Apple 거래 서명이 올바르지 않습니다.");
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw ApiException.badRequest("INVALID_SIGNED_TRANSACTION_INFO", "Apple 거래 서명을 검증할 수 없습니다.");
        }
    }

    private byte[] rawEcdsaSignatureToDer(byte[] rawSignature) {
        if (rawSignature.length != 64) {
            throw ApiException.badRequest("INVALID_SIGNED_TRANSACTION_INFO", "Apple 거래 서명 길이가 올바르지 않습니다.");
        }
        byte[] r = derInteger(rawSignature, 0, 32);
        byte[] s = derInteger(rawSignature, 32, 32);
        ByteArrayOutputStream sequence = new ByteArrayOutputStream();
        sequence.write(0x30);
        sequence.write(2 + r.length + 2 + s.length);
        sequence.write(0x02);
        sequence.write(r.length);
        sequence.writeBytes(r);
        sequence.write(0x02);
        sequence.write(s.length);
        sequence.writeBytes(s);
        return sequence.toByteArray();
    }

    private byte[] derInteger(byte[] source, int offset, int length) {
        byte[] value = new byte[length];
        System.arraycopy(source, offset, value, 0, length);
        return new BigInteger(1, value).toByteArray();
    }

    private Map<String, Object> decodeJwtPayload(String jws) {
        try {
            String[] parts = jws.split("\\.");
            if (parts.length < 2) {
                throw ApiException.badRequest("INVALID_SIGNED_TRANSACTION_INFO", "signedTransactionInfo 형식이 올바르지 않습니다.");
            }
            return decodeJwtPart(parts[1]);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw ApiException.badRequest("INVALID_SIGNED_TRANSACTION_INFO", "signedTransactionInfo를 해석할 수 없습니다.");
        }
    }

    private Map<String, Object> decodeJwtPart(String encoded) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(padBase64(encoded));
            return objectMapper.readValue(decoded, new TypeReference<>() {
            });
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

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("[^A-Za-z0-9]", "").toLowerCase();
    }
}
