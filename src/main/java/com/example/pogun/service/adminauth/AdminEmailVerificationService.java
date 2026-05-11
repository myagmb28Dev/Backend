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
        String origin = request != null ? request.getHeader("Origin") : null;
        if (origin != null && !origin.isBlank()) {
            String mapped = adminConsoleProperties.getEmailVerification().getContinueUrlMappings().get(origin.trim());
            if (mapped != null && !mapped.isBlank()) {
                return mapped.trim();
            }
        }
        String fallback = adminConsoleProperties.getEmailVerification().getDefaultContinueUrl();
        return fallback == null || fallback.isBlank() ? null : fallback.trim();
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
