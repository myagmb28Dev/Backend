package com.example.pogun.service.adminauth;

import com.example.pogun.config.FirebaseAuthProperties;
import com.example.pogun.dto.common.ApiResponse.ApiException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class FirebasePasswordSignInClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final FirebaseAuthProperties firebaseAuthProperties;
    private final FirebaseAuth firebaseAuth;

    public FirebasePasswordSignInResult signIn(String email, String password) {
        String baseUrl = resolveBaseUrl();
        String apiKey = resolveApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw ApiException.internal("FIREBASE_WEB_API_KEY_MISSING", "Firebase Web API key가 설정되지 않았습니다.");
        }

        try {
            String responseBody = WebClient.builder()
                    .baseUrl(baseUrl)
                    .build()
                    .post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1/accounts:signInWithPassword")
                            .queryParam("key", apiKey)
                            .build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of(
                            "email", email,
                            "password", password,
                            "returnSecureToken", true
                    ))
                    .retrieve()
                        .bodyToMono(String.class)
                    .block();

                    if (responseBody == null || responseBody.isBlank()) {
                throw ApiException.unauthorized("INVALID_CREDENTIALS", "관리자 로그인에 실패했습니다.");
            }

                    JsonNode response = OBJECT_MAPPER.readTree(responseBody);

            return new FirebasePasswordSignInResult(
                    text(response, "localId"),
                    text(response, "email"),
                    text(response, "idToken"),
                    text(response, "refreshToken"),
                    resolveEmailVerified(text(response, "localId"), response.path("emailVerified").asBoolean(false))
            );
        } catch (WebClientResponseException e) {
            throw mapError(e);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Firebase password sign-in failed unexpectedly", e);
            throw ApiException.internal("FIREBASE_SIGN_IN_FAILED", "Firebase 관리자 로그인 처리 중 오류가 발생했습니다.");
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

    private boolean resolveEmailVerified(String localId, boolean fallback) {
        if (!firebaseAuthProperties.isEmulatorMode() || localId == null || localId.isBlank()) {
            return fallback;
        }
        try {
            return firebaseAuth.getUser(localId).isEmailVerified();
        } catch (FirebaseAuthException e) {
            return fallback;
        }
    }

    private RuntimeException mapError(WebClientResponseException e) {
        try {
            JsonNode json = OBJECT_MAPPER.readTree(e.getResponseBodyAsString());
            String message = json.path("error").path("message").asText("");
            return switch (message) {
                case "INVALID_PASSWORD", "EMAIL_NOT_FOUND", "INVALID_LOGIN_CREDENTIALS" ->
                        ApiException.unauthorized("INVALID_CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않습니다.");
                case "TOO_MANY_ATTEMPTS_TRY_LATER" ->
                        new ApiException(HttpStatus.TOO_MANY_REQUESTS, "LOCKED", "로그인 시도가 너무 많습니다.", null);
                default -> ApiException.unauthorized("INVALID_CREDENTIALS", "관리자 로그인에 실패했습니다: " + message);
            };
        } catch (Exception ignored) {
            return ApiException.unauthorized("INVALID_CREDENTIALS", "관리자 로그인에 실패했습니다.");
        }
    }

    private String text(JsonNode node, String field) {
        String value = node.path(field).asText("");
        return value == null || value.isBlank() ? null : value;
    }

    public record FirebasePasswordSignInResult(
            String localId,
            String email,
            String idToken,
            String refreshToken,
            boolean emailVerified
    ) {
    }
}
