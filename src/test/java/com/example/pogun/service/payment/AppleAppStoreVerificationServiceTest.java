package com.example.pogun.service.payment;

import com.example.pogun.config.payment.PaymentProperties;
import com.example.pogun.dto.payment.PaymentVerifyRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AppleAppStoreVerificationServiceTest {

    private static final String TEST_ROOT_CERTIFICATE_BASE64 = "MIIBUzCB+qADAgECAgkAqdrPq9ovIMcwCgYIKoZIzj0EAwIwHTEbMBkGA1UEAxMSUG9ndW4gVGVzdCBSb290IENBMB4XDTI2MDEwMTAwMDAwMFoXDTMxMDEwMTAwMDAwMFowHTEbMBkGA1UEAxMSUG9ndW4gVGVzdCBSb290IENBMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEU3rfFSDKp+rFMY7Dd9P3FkUfR5Y9wPJrnG3EoSsLv6hJMKPgpqDPCk1q4p7fA4cFOthWZTLJT+a7a9vsAVRi0KMjMCEwDwYDVR0TAQH/BAUwAwEB/zAOBgNVHQ8BAf8EBAMCAQYwCgYIKoZIzj0EAwIDSAAwRQIgByT8qTJhh9wTku8ljRf4Vi7FFPNNOqe2TYrNFhIPq9kCIQCLGH9C3Ctzad/JuE4dGvKM8b14Dh4Ih5naJbKdlob5pA==";
    private static final String SIGNED_TRANSACTION_INFO = "eyJhbGciOiJFUzI1NiIsIng1YyI6WyJNSUlCV1RDQ0FRQ2dBd0lCQWdJSUFRSURCQVVHQndnd0NnWUlLb1pJemowRUF3SXdIVEViTUJrR0ExVUVBeE1TVUc5bmRXNGdWR1Z6ZENCU2IyOTBJRU5CTUI0WERUSTJNREV3TVRBd01EQXdNRm9YRFRNd01ERXdNVEF3TURBd01Gb3dKekVsTUNNR0ExVUVBeE1jVUc5bmRXNGdWR1Z6ZENCQmNIQWdVM1J2Y21VZ1UybG5ibWx1WnpCWk1CTUdCeXFHU000OUFnRUdDQ3FHU000OUF3RUhBMElBQkhJVVBCRmNYV0tpREtibDVNVkZ4OWk5QURPWG9Keks3eHRMRGMwdUVTK3MrbEdWWVU3R2V5cTFZWFFvT09RR2xDOGZxUkhGQ2pZSzNENnQvYUpKSjRpaklEQWVNQXdHQTFVZEV3RUIvd1FDTUFBd0RnWURWUjBQQVFIL0JBUURBZ2VBTUFvR0NDcUdTTTQ5QkFNQ0EwY0FNRVFDSURDRGcydGl0N0k5QWZHaWZ6cWhMRjFNOEorSWw2M0JONTVheHVvWmorOEtBaUJSSmtxQjhPSWVrUmVqZGVuYkJ0NGFadzB0MW13dHZlQ0VTa2ROSW1hZzlRPT0iLCJNSUlCVXpDQitxQURBZ0VDQWdrQXFkclBxOW92SU1jd0NnWUlLb1pJemowRUF3SXdIVEViTUJrR0ExVUVBeE1TVUc5bmRXNGdWR1Z6ZENCU2IyOTBJRU5CTUI0WERUSTJNREV3TVRBd01EQXdNRm9YRFRNeE1ERXdNVEF3TURBd01Gb3dIVEViTUJrR0ExVUVBeE1TVUc5bmRXNGdWR1Z6ZENCU2IyOTBJRU5CTUZrd0V3WUhLb1pJemowQ0FRWUlLb1pJemowREFRY0RRZ0FFVTNyZkZTREtwK3JGTVk3RGQ5UDNGa1VmUjVZOXdQSnJuRzNFb1NzTHY2aEpNS1BncHFEUENrMXE0cDdmQTRjRk90aFdaVExKVCthN2E5dnNBVlJpMEtNak1DRXdEd1lEVlIwVEFRSC9CQVV3QXdFQi96QU9CZ05WSFE4QkFmOEVCQU1DQVFZd0NnWUlLb1pJemowRUF3SURTQUF3UlFJZ0J5VDhxVEpoaDl3VGt1OGxqUmY0Vmk3RkZQTk5PcWUyVFlyTkZoSVBxOWtDSVFDTEdIOUMzQ3R6YWQvSnVFNGRHdktNOGIxNERoNEloNW5hSmJLZGxvYjVwQT09Il19.eyJ0cmFuc2FjdGlvbklkIjoidHgtdGVzdC0xIiwib3JpZ2luYWxUcmFuc2FjdGlvbklkIjoib3JpZy10ZXN0LTEiLCJwcm9kdWN0SWQiOiJwYXdfYWlfY3JlZGl0c18zXzMwMCIsImJ1bmRsZUlkIjoiY29tLmV4YW1wbGUucG9ndW4iLCJlbnZpcm9ubWVudCI6IlNhbmRib3giLCJ0eXBlIjoiQ29uc3VtYWJsZSIsInB1cmNoYXNlRGF0ZSI6MTc4MTc0MDgwMDAwMH0.MXHfvHX9-pIr62Di3nVxPLhYFYyRHRqh9OmWHogtQawY-JpyT3iBZm9mUXboAVD-qUdpv5velIMuKiKwy8Dxkw";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private KeyPair jwtSigningKeyPair;

    @BeforeEach
    void setUp() throws Exception {
        jwtSigningKeyPair = keyPair();
    }

    @Test
    void resolveBaseUrl_usesCurrentStoreKitHosts() throws Exception {
        AppleAppStoreVerificationService service = newService();
        PaymentProperties.Apple apple = new PaymentProperties.Apple();
        Method method = AppleAppStoreVerificationService.class
                .getDeclaredMethod("resolveBaseUrl", PaymentProperties.Apple.class);
        method.setAccessible(true);

        apple.setEnvironment("production");
        assertThat(method.invoke(service, apple)).isEqualTo("https://api.storekit.apple.com");

        apple.setEnvironment("sandbox");
        assertThat(method.invoke(service, apple)).isEqualTo("https://api.storekit-sandbox.apple.com");
    }

    @Test
    void createSignedJwt_encodesEs256SignatureAsJoseRawSignature() throws Exception {
        PaymentProperties.Apple apple = new PaymentProperties.Apple();
        apple.setKeyId("KEY1234567");
        apple.setIssuerId("issuer-id");
        apple.setBundleId("com.example.pogun");
        apple.setPrivateKeyBase64(privateKeyBase64(jwtSigningKeyPair.getPrivate()));

        AppleAppStoreVerificationService service = newService();
        Method method = AppleAppStoreVerificationService.class
                .getDeclaredMethod("createSignedJwt", PaymentProperties.Apple.class);
        method.setAccessible(true);

        String jwt = (String) method.invoke(service, apple);
        String[] parts = jwt.split("\\.");
        assertThat(parts).hasSize(3);

        Map<String, Object> header = decodeJwtPart(parts[0]);
        assertThat(header)
                .containsEntry("alg", "ES256")
                .containsEntry("kid", "KEY1234567")
                .containsEntry("typ", "JWT");

        Map<String, Object> payload = decodeJwtPart(parts[1]);
        assertThat(payload)
                .containsEntry("iss", "issuer-id")
                .containsEntry("aud", "appstoreconnect-v1")
                .containsEntry("bid", "com.example.pogun");

        byte[] rawSignature = Base64.getUrlDecoder().decode(padBase64(parts[2]));
        assertThat(rawSignature).hasSize(64);

        Signature verifier = Signature.getInstance("SHA256withECDSA");
        verifier.initVerify(jwtSigningKeyPair.getPublic());
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8));
        assertThat(verifier.verify(rawEcdsaSignatureToDer(rawSignature))).isTrue();
    }

    @Test
    void verify_usesStoreKitUrlJwtAuthorizationAndSignedTransactionVerification() throws Exception {
        PaymentProperties properties = new PaymentProperties();
        properties.getApple().setEnabled(true);
        properties.getApple().setKeyId("KEY1234567");
        properties.getApple().setIssuerId("issuer-id");
        properties.getApple().setBundleId("com.example.pogun");
        properties.getApple().setEnvironment("sandbox");
        properties.getApple().setPrivateKeyBase64(privateKeyBase64(jwtSigningKeyPair.getPrivate()));

        AtomicReference<ClientRequest> capturedRequest = new AtomicReference<>();
        WebClient.Builder webClientBuilder = WebClient.builder()
                .exchangeFunction(request -> {
                    capturedRequest.set(request);
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .body("{\"signedTransactionInfo\":\"" + SIGNED_TRANSACTION_INFO + "\"}")
                            .build());
                });
        AppleAppStoreVerificationService service = newServiceWithTrustAnchor(properties, webClientBuilder);

        PaymentVerifyRequest request = new PaymentVerifyRequest();
        request.setPlatform("ios");
        request.setProductId("paw_ai_credits_3_300");
        request.setTransactionId("tx-test-1");

        VerifiedPurchase verifiedPurchase = service.verify(request);

        assertThat(capturedRequest.get().url().toString())
                .isEqualTo("https://api.storekit-sandbox.apple.com/inApps/v1/transactions/tx-test-1");
        assertThat(capturedRequest.get().headers().getFirst(HttpHeaders.AUTHORIZATION))
                .startsWith("Bearer ");
        assertThat(verifiedPurchase.platform()).isEqualTo(com.example.pogun.entity.payment.enums.PaymentPlatform.IOS);
        assertThat(verifiedPurchase.productId()).isEqualTo("paw_ai_credits_3_300");
        assertThat(verifiedPurchase.transactionId()).isEqualTo("tx-test-1");
        assertThat(verifiedPurchase.originalTransactionId()).isEqualTo("orig-test-1");
        assertThat(verifiedPurchase.environment()).isEqualTo("Sandbox");
        assertThat(verifiedPurchase.rawPayload()).isEqualTo(SIGNED_TRANSACTION_INFO);
    }

    @Test
    void verifyAndDecodeAppleSignedPayload_validatesX5cChainAndSignature() throws Exception {
        AppleAppStoreVerificationService service = newServiceWithTrustAnchor(new PaymentProperties(), WebClient.builder());
        Method method = AppleAppStoreVerificationService.class
                .getDeclaredMethod("verifyAndDecodeAppleSignedPayload", String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) method.invoke(service, SIGNED_TRANSACTION_INFO);

        assertThat(payload)
                .containsEntry("transactionId", "tx-test-1")
                .containsEntry("originalTransactionId", "orig-test-1")
                .containsEntry("productId", "paw_ai_credits_3_300")
                .containsEntry("bundleId", "com.example.pogun")
                .containsEntry("environment", "Sandbox")
                .containsEntry("type", "Consumable");
    }

    private AppleAppStoreVerificationService newService() {
        return new AppleAppStoreVerificationService(new PaymentProperties(), WebClient.builder(), objectMapper);
    }

    private AppleAppStoreVerificationService newServiceWithTrustAnchor(PaymentProperties properties, WebClient.Builder webClientBuilder) throws Exception {
        X509Certificate rootCertificate = certificate(TEST_ROOT_CERTIFICATE_BASE64);
        return new AppleAppStoreVerificationService(properties, webClientBuilder, objectMapper) {
            @Override
            protected Set<TrustAnchor> trustAnchors() {
                return Set.of(new TrustAnchor(rootCertificate, null));
            }
        };
    }

    private KeyPair keyPair() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
        keyPairGenerator.initialize(new ECGenParameterSpec("secp256r1"));
        return keyPairGenerator.generateKeyPair();
    }

    private X509Certificate certificate(String encodedCertificate) throws Exception {
        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        return (X509Certificate) certificateFactory.generateCertificate(
                new ByteArrayInputStream(Base64.getDecoder().decode(encodedCertificate))
        );
    }

    private Map<String, Object> decodeJwtPart(String encoded) throws Exception {
        return objectMapper.readValue(Base64.getUrlDecoder().decode(padBase64(encoded)), new TypeReference<>() {
        });
    }

    private String privateKeyBase64(PrivateKey privateKey) {
        String encodedKey = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8))
                .encodeToString(privateKey.getEncoded());
        String pem = "-----BEGIN PRIVATE KEY-----\n" + encodedKey + "\n-----END PRIVATE KEY-----\n";
        return Base64.getEncoder().encodeToString(pem.getBytes(StandardCharsets.UTF_8));
    }

    private String padBase64(String value) {
        int remainder = value.length() % 4;
        if (remainder == 0) {
            return value;
        }
        return value + "=".repeat(4 - remainder);
    }

    private byte[] rawEcdsaSignatureToDer(byte[] rawSignature) {
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
}
