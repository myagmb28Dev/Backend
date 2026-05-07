package com.example.pogun.service.auth;

import com.example.pogun.config.FirebaseAuthProperties;
import com.example.pogun.dto.auth.TokenRefreshResponse;
import com.example.pogun.dto.common.ApiResponse.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class FirebaseTokenRefreshService {

    private final FirebaseAuthProperties firebaseAuthProperties;
    private final WebClient.Builder webClientBuilder;

    public TokenRefreshResponse refresh(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            throw ApiException.unauthorized("TOKEN_REFRESH_REQUIRED", "리프레시 토큰이 필요합니다.");
        }
        String apiKey = resolveApiKey();
        String baseUrl = resolveBaseUrl();

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken.trim());

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

    private String resolveApiKey() {
        if (firebaseAuthProperties.isAllowEmulator()) {
            return "fake-api-key";
        }
        String apiKey = firebaseAuthProperties.getWebApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw ApiException.internal("FIREBASE_WEB_API_KEY_MISSING", "Firebase Web API key가 설정되지 않았습니다.");
        }
        return apiKey;
    }

    private String resolveBaseUrl() {
        if (firebaseAuthProperties.isAllowEmulator()) {
            String emulatorHost = System.getenv("FIREBASE_AUTH_EMULATOR_HOST");
            if (emulatorHost == null || emulatorHost.isBlank()) {
                throw ApiException.internal("FIREBASE_EMULATOR_HOST_MISSING", "FIREBASE_AUTH_EMULATOR_HOST가 설정되지 않았습니다.");
            }
            return "http://" + emulatorHost + "/securetoken.googleapis.com";
        }
        return "https://securetoken.googleapis.com";
    }
}
