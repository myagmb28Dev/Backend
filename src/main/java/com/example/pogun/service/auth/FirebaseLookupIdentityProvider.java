package com.example.pogun.service.auth;

import com.example.pogun.config.FirebaseAuthProperties;
import com.example.pogun.dto.common.ApiResponse.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class FirebaseLookupIdentityProvider implements FirebaseIdentityProvider {

    private final FirebaseAuthProperties firebaseAuthProperties;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    @Override
    public FirebaseIdentityService.FirebaseIdentity verifyIdToken(String idToken, boolean checkRevoked) {
        String apiKey = firebaseAuthProperties.getWebApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw ApiException.internal("FIREBASE_WEB_API_KEY_MISSING", "Firebase Web API key가 설정되지 않았습니다.");
        }

        try {
            long startTime = System.currentTimeMillis();
            String responseBody = webClientBuilder
                    .baseUrl("https://identitytoolkit.googleapis.com")
                    .build()
                    .post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1/accounts:lookup")
                            .queryParam("key", apiKey)
                            .build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("idToken", idToken))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(10));
            long endTime = System.currentTimeMillis();
            System.out.println("[FirebaseLookup] REST call completed in " + (endTime - startTime) + "ms");

            if (responseBody == null || responseBody.isBlank()) {
                throw new IllegalArgumentException("Firebase lookup 응답이 비어 있습니다.");
            }

            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode user = root.path("users").isArray() && root.path("users").size() > 0
                    ? root.path("users").get(0)
                    : null;
            if (user == null || user.isMissingNode()) {
                throw new IllegalArgumentException("Firebase lookup에서 사용자를 찾지 못했습니다.");
            }

            String uid = text(user, "localId");
            String email = text(user, "email");
            String displayName = text(user, "displayName");
            String photoUrl = text(user, "photoUrl");
            String signInProvider = null;

            List<FirebaseIdentityService.ProviderIdentity> providers = new ArrayList<>();
            JsonNode providerUserInfo = user.path("providerUserInfo");
            if (providerUserInfo.isArray()) {
                for (JsonNode info : providerUserInfo) {
                    String providerId = text(info, "providerId");
                    if (providerId == null || providerId.isBlank()) {
                        continue;
                    }
                    providers.add(new FirebaseIdentityService.ProviderIdentity(
                            providerId,
                            text(info, "rawId"),
                            text(info, "email")
                    ));
                    if (signInProvider == null || signInProvider.isBlank()) {
                        signInProvider = providerId;
                    }
                }
            }

            if ((signInProvider == null || signInProvider.isBlank()) && !providers.isEmpty()) {
                signInProvider = providers.get(0).providerId();
            }
            if ((signInProvider == null || signInProvider.isBlank()) && email != null) {
                signInProvider = "password";
            }

            return new FirebaseIdentityService.FirebaseIdentity(
                    uid,
                    email,
                    displayName,
                    photoUrl,
                    signInProvider,
                    providers,
                    Map.of()
            );
        } catch (WebClientResponseException e) {
            throw new IllegalArgumentException("Firebase REST token lookup 실패: " + e.getResponseBodyAsString(), e);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Firebase REST token lookup 실패", e);
        }
    }

    private String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        String value = node.path(field).asText("");
        return value == null || value.isBlank() ? null : value;
    }
}
