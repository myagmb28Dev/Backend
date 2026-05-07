package com.example.pogun.service.auth;

import com.example.pogun.config.FirebaseAuthProperties;
import com.example.pogun.dto.auth.TokenRefreshResponse;
import com.example.pogun.dto.common.ApiResponse.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FirebaseTokenRefreshServiceTest {

    @Test
    void refreshReturnsTokenResponseFromProductionEndpoint() {
        FirebaseTokenRefreshService service = new FirebaseTokenRefreshService(
                productionProperties(),
                WebClient.builder().exchangeFunction(request -> {
                    assertThat(request.url().toString()).contains("https://securetoken.googleapis.com/v1/token");
                    assertThat(request.url().getQuery()).contains("key=test-api-key");
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .body("""
                                    {
                                      "id_token": "new-id-token",
                                      "refresh_token": "new-refresh-token",
                                      "expires_in": "3600",
                                      "user_id": "firebase-uid",
                                      "project_id": "pogeun-fire"
                                    }
                                    """)
                            .build());
                })
        );

        TokenRefreshResponse response = service.refresh(" old-refresh-token ");

        assertThat(response.idToken()).isEqualTo("new-id-token");
        assertThat(response.refreshToken()).isEqualTo("new-refresh-token");
        assertThat(response.firebaseUid()).isEqualTo("firebase-uid");
        assertThat(response.projectId()).isEqualTo("pogeun-fire");
    }

    @Test
    void refreshConvertsFirebaseRejectionToUnauthorized() {
        FirebaseTokenRefreshService service = new FirebaseTokenRefreshService(
                productionProperties(),
                WebClient.builder().exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.BAD_REQUEST)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body("""
                                {
                                  "error": {
                                    "message": "INVALID_REFRESH_TOKEN"
                                  }
                                }
                                """)
                        .build()))
        );

        assertThatThrownBy(() -> service.refresh("bad-refresh-token"))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> {
                    ApiException apiException = (ApiException) error;
                    assertThat(apiException.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(apiException.getCode()).isEqualTo("TOKEN_REFRESH_FAILED");
                });
    }

    @Test
    void refreshConvertsUnexpectedClientFailureToUnauthorized() {
        FirebaseTokenRefreshService service = new FirebaseTokenRefreshService(
                productionProperties(),
                WebClient.builder().exchangeFunction(request -> Mono.error(new IllegalStateException("client failed")))
        );

        assertThatThrownBy(() -> service.refresh("refresh-token"))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> {
                    ApiException apiException = (ApiException) error;
                    assertThat(apiException.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(apiException.getCode()).isEqualTo("TOKEN_REFRESH_FAILED");
                });
    }

    private FirebaseAuthProperties productionProperties() {
        FirebaseAuthProperties properties = new FirebaseAuthProperties();
        properties.setMode(FirebaseAuthProperties.Mode.PRODUCTION);
        properties.setWebApiKey("test-api-key");
        return properties;
    }
}
