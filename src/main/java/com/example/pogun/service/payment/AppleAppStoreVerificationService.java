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

    private static final List<String> APPLE_ROOT_CERTIFICATES_BASE64 = List.of(
            "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"
    );

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
                ? "https://api.storekit.apple.com"
                : "https://api.storekit-sandbox.apple.com";
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
            String signatureEncoded = base64Url(derEcdsaSignatureToRaw(signature.sign()));
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
            PKIXParameters parameters = new PKIXParameters(trustAnchors());
            parameters.setRevocationEnabled(false);
            CertPathValidator.getInstance("PKIX").validate(certPath, parameters);
        } catch (Exception e) {
            throw ApiException.badRequest("INVALID_SIGNED_TRANSACTION_INFO", "Apple 거래 서명 인증서 체인을 검증할 수 없습니다.");
        }
    }

    protected Set<TrustAnchor> trustAnchors() throws Exception {
        Set<TrustAnchor> trustAnchors = defaultTrustAnchors();
        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        for (String encodedCertificate : APPLE_ROOT_CERTIFICATES_BASE64) {
            X509Certificate certificate = (X509Certificate) certificateFactory.generateCertificate(
                    new ByteArrayInputStream(Base64.getDecoder().decode(encodedCertificate))
            );
            trustAnchors.add(new TrustAnchor(certificate, null));
        }
        return trustAnchors;
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

    private byte[] derEcdsaSignatureToRaw(byte[] derSignature) {
        int[] indexRef = new int[] {0};
        if (readDerByte(derSignature, indexRef) != 0x30) {
            throw ApiException.internal("APPLE_JWT_SIGNING_FAILED", "Apple App Store JWT 생성에 실패했습니다.");
        }

        int sequenceLength = readDerLength(derSignature, indexRef);
        if (sequenceLength != derSignature.length - indexRef[0]) {
            throw ApiException.internal("APPLE_JWT_SIGNING_FAILED", "Apple App Store JWT 생성에 실패했습니다.");
        }

        byte[] rawSignature = new byte[64];
        readDerIntegerToRaw(derSignature, indexRef, rawSignature, 0);
        readDerIntegerToRaw(derSignature, indexRef, rawSignature, 32);
        if (indexRef[0] != derSignature.length) {
            throw ApiException.internal("APPLE_JWT_SIGNING_FAILED", "Apple App Store JWT 생성에 실패했습니다.");
        }
        return rawSignature;
    }

    private void readDerIntegerToRaw(byte[] derSignature, int[] indexRef, byte[] rawSignature, int destinationOffset) {
        if (readDerByte(derSignature, indexRef) != 0x02) {
            throw ApiException.internal("APPLE_JWT_SIGNING_FAILED", "Apple App Store JWT 생성에 실패했습니다.");
        }
        int integerLength = readDerLength(derSignature, indexRef);
        if (integerLength <= 0 || indexRef[0] + integerLength > derSignature.length) {
            throw ApiException.internal("APPLE_JWT_SIGNING_FAILED", "Apple App Store JWT 생성에 실패했습니다.");
        }

        int integerOffset = indexRef[0];
        indexRef[0] += integerLength;
        while (integerLength > 1 && derSignature[integerOffset] == 0) {
            integerOffset++;
            integerLength--;
        }
        if (integerLength > 32) {
            throw ApiException.internal("APPLE_JWT_SIGNING_FAILED", "Apple App Store JWT 생성에 실패했습니다.");
        }
        System.arraycopy(derSignature, integerOffset, rawSignature, destinationOffset + 32 - integerLength, integerLength);
    }

    private int readDerLength(byte[] derSignature, int[] indexRef) {
        int firstByte = readDerByte(derSignature, indexRef);
        if ((firstByte & 0x80) == 0) {
            return firstByte;
        }

        int byteCount = firstByte & 0x7F;
        if (byteCount == 0 || byteCount > 2 || indexRef[0] + byteCount > derSignature.length) {
            throw ApiException.internal("APPLE_JWT_SIGNING_FAILED", "Apple App Store JWT 생성에 실패했습니다.");
        }

        int length = 0;
        for (int i = 0; i < byteCount; i++) {
            length = (length << 8) | readDerByte(derSignature, indexRef);
        }
        return length;
    }

    private int readDerByte(byte[] derSignature, int[] indexRef) {
        if (indexRef[0] >= derSignature.length) {
            throw ApiException.internal("APPLE_JWT_SIGNING_FAILED", "Apple App Store JWT 생성에 실패했습니다.");
        }
        return derSignature[indexRef[0]++] & 0xFF;
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
