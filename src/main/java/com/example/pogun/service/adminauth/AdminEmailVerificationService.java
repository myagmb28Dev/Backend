package com.example.pogun.service.adminauth;

import com.example.pogun.config.AdminConsoleProperties;
import com.example.pogun.config.FirebaseAuthProperties;
import com.example.pogun.dto.common.ApiResponse.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class AdminEmailVerificationService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final FirebaseAuthProperties firebaseAuthProperties;
    private final AdminConsoleProperties adminConsoleProperties;
    private final WebClient.Builder webClientBuilder;

    public void sendVerificationEmail(String idToken, HttpServletRequest request) {
        String baseUrl = resolveBaseUrl();
        String apiKey = resolveApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw ApiException.internal("FIREBASE_WEB_API_KEY_MISSING", "Firebase Web API key가 설정되지 않았습니다.");
        }

        try {
            String continueUrl = resolveContinueUrl(request);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("requestType", "VERIFY_EMAIL");
            payload.put("idToken", idToken);
            if (continueUrl != null && !continueUrl.isBlank()) {
                payload.put("continueUrl", continueUrl);
            }

            String responseBody = webClientBuilder
                    .baseUrl(baseUrl)
                    .build()
                    .post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1/accounts:sendOobCode")
                            .queryParam("key", apiKey)
                            .build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (responseBody == null || responseBody.isBlank()) {
                throw ApiException.internal("EMAIL_VERIFICATION_SEND_FAILED", "이메일 인증 메일 발송에 실패했습니다.");
            }
        } catch (WebClientResponseException e) {
            throw mapError(e);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to send Firebase verification email", e);
            throw ApiException.internal("EMAIL_VERIFICATION_SEND_FAILED", "이메일 인증 메일 발송에 실패했습니다.");
        }
    }

    private String resolveContinueUrl(HttpServletRequest request) {
        Map<String, String> mappings = adminConsoleProperties.getEmailVerification().getContinueUrlMappings();

        String origin = request != null ? request.getHeader("Origin") : null;
        String normalizedOrigin = normalizeOrigin(origin);
        if (normalizedOrigin != null) {
            String mapped = mappings.get(normalizedOrigin);
            if (mapped != null && !mapped.isBlank()) {
                return mapped.trim();
            }
        }

        String referer = request != null ? request.getHeader("Referer") : null;
        String refererOrigin = normalizeOriginFromReferer(referer);
        if (refererOrigin != null) {
            String mapped = mappings.get(refererOrigin);
            if (mapped != null && !mapped.isBlank()) {
                return mapped.trim();
            }
        }

        String fallback = adminConsoleProperties.getEmailVerification().getDefaultContinueUrl();
        return fallback == null || fallback.isBlank() ? null : fallback.trim();
    }

    private String normalizeOrigin(String rawOrigin) {
        if (rawOrigin == null || rawOrigin.isBlank()) {
            return null;
        }
        String trimmed = rawOrigin.trim();
        try {
            URI uri = URI.create(trimmed);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            int port = uri.getPort();
            if (scheme == null || host == null) {
                return trimmed;
            }
            if (port < 0) {
                return scheme + "://" + host;
            }
            return scheme + "://" + host + ":" + port;
        } catch (IllegalArgumentException ex) {
            return trimmed;
        }
    }

    private String normalizeOriginFromReferer(String referer) {
        if (referer == null || referer.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(referer.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            int port = uri.getPort();
            if (scheme == null || host == null) {
                return null;
            }
            if (port < 0) {
                return scheme + "://" + host;
            }
            return scheme + "://" + host + ":" + port;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    String resolveBaseUrl() {
        if (!firebaseAuthProperties.isEmulatorMode()) {
            return "https://identitytoolkit.googleapis.com";
        }
        String emulatorHost = System.getenv("FIREBASE_AUTH_EMULATOR_HOST");
        if (emulatorHost == null || emulatorHost.isBlank()) {
            throw ApiException.internal("FIREBASE_AUTH_EMULATOR_HOST_MISSING", "Auth Emulator host가 설정되지 않았습니다.");
        }
        return "http://" + emulatorHost.trim() + "/identitytoolkit.googleapis.com";
    }

    String resolveApiKey() {
        if (firebaseAuthProperties.isEmulatorMode()) {
            return "fake-api-key";
        }
        return firebaseAuthProperties.getWebApiKey();
    }

    private RuntimeException mapError(WebClientResponseException e) {
        try {
            JsonNode json = OBJECT_MAPPER.readTree(e.getResponseBodyAsString());
            String message = json.path("error").path("message").asText("");
            return switch (message) {
                case "INVALID_ID_TOKEN", "USER_NOT_FOUND" ->
                        ApiException.unauthorized("EMAIL_VERIFICATION_FAILED", "이메일 인증 메일 발송에 실패했습니다.");
                default -> ApiException.internal("EMAIL_VERIFICATION_FAILED", "이메일 인증 메일 발송에 실패했습니다: " + message);
            };
        } catch (Exception ignored) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED || e.getStatusCode() == HttpStatus.FORBIDDEN) {
                return ApiException.unauthorized("EMAIL_VERIFICATION_FAILED", "이메일 인증 메일 발송에 실패했습니다.");
            }
            return ApiException.internal("EMAIL_VERIFICATION_FAILED", "이메일 인증 메일 발송에 실패했습니다.");
        }
    }
}
