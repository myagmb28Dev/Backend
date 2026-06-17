package com.example.pogun.service.auth;

import com.example.pogun.config.firebase.FirebaseAuthProperties;
import com.example.pogun.dto.auth.TokenRefreshResponse;
import com.example.pogun.dto.common.ApiResponse.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class FirebaseTokenRefreshService {

    private final FirebaseAuthProperties firebaseAuthProperties;
    private final WebClient.Builder webClientBuilder;

    public TokenRefreshResponse refresh(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            throw ApiException.unauthorized("TOKEN_REFRESH_REQUIRED", "리프레시 토큰이 필요합니다.");
        }
        String trimmedRefreshToken = refreshToken.trim();
        String apiKey = firebaseAuthProperties.isEmulatorMode() ? "fake-api-key" : resolveProductionApiKey();
        String baseUrl = firebaseAuthProperties.isEmulatorMode() ? resolveEmulatorBaseUrl() : "https://securetoken.googleapis.com";
        return refreshWithEndpoint(trimmedRefreshToken, apiKey, baseUrl);
    }

    private TokenRefreshResponse refreshWithEndpoint(String refreshToken, String apiKey, String baseUrl) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = webClientBuilder
                    .baseUrl(baseUrl)
                    .build()
                    .post()
                    .uri(uriBuilder -> uriBuilder.path("/v1/token").queryParam("key", apiKey).build())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(form))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (payload == null) {
                throw ApiException.unauthorized("TOKEN_REFRESH_FAILED", "토큰 갱신 응답이 비어 있습니다.");
            }

            String idToken = asString(payload.get("id_token"));
            String newRefreshToken = asString(payload.get("refresh_token"));
            String expiresInRaw = asString(payload.get("expires_in"));
            String firebaseUid = asString(payload.get("user_id"));
            String projectId = asString(payload.get("project_id"));

            if (idToken == null || idToken.isBlank() || newRefreshToken == null || newRefreshToken.isBlank()) {
                throw ApiException.unauthorized("TOKEN_REFRESH_FAILED", "토큰 갱신 응답이 올바르지 않습니다.");
            }

            long expiresIn = parseLongOrDefault(expiresInRaw, 3600L);
            return new TokenRefreshResponse(idToken, newRefreshToken, expiresIn, firebaseUid, projectId);
        } catch (WebClientResponseException e) {
            log.warn("Firebase token refresh rejected: status={}", e.getStatusCode().value());
            throw ApiException.unauthorized("TOKEN_REFRESH_FAILED", "Firebase 토큰 갱신에 실패했습니다.");
        } catch (WebClientRequestException e) {
            log.warn("Firebase token refresh request failed: {}", e.getMessage());
            throw ApiException.unauthorized("TOKEN_REFRESH_FAILED", "Firebase 토큰 갱신에 실패했습니다.");
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("Firebase token refresh failed unexpectedly", e);
            throw ApiException.unauthorized("TOKEN_REFRESH_FAILED", "Firebase 토큰 갱신에 실패했습니다.");
        }
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private long parseLongOrDefault(String raw, long defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private String resolveProductionApiKey() {
        String apiKey = firebaseAuthProperties.getWebApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw ApiException.internal("FIREBASE_WEB_API_KEY_MISSING", "Firebase Web API key가 설정되지 않았습니다.");
        }
        return apiKey;
    }

    private String resolveEmulatorBaseUrl() {
        String emulatorHost = System.getenv("FIREBASE_AUTH_EMULATOR_HOST");
        if (emulatorHost == null || emulatorHost.isBlank()) {
            throw ApiException.internal("FIREBASE_EMULATOR_HOST_MISSING", "FIREBASE_AUTH_EMULATOR_HOST가 설정되지 않았습니다.");
        }
        return "http://" + emulatorHost + "/securetoken.googleapis.com";
    }
}
