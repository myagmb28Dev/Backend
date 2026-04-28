package com.example.pogun.service.adminauth;

import com.example.pogun.config.FirebaseAuthProperties;
import com.example.pogun.dto.common.ApiResponse.ApiException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserInfo;
import com.google.firebase.auth.UserRecord;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FirebasePasswordSignInClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        stopServer(server);
    }

    @Test
    void signIn_parsesJsonResponseFromHttpBody() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/accounts:signInWithPassword", exchange -> {
            byte[] bodyBytes = exchange.getRequestBody().readAllBytes();
            String requestBody = new String(bodyBytes, StandardCharsets.UTF_8);
            assertThat(requestBody).contains("\"email\":\"admin@local.dev\"");
            assertThat(requestBody).contains("\"password\":\"Test1234!\"");

            String response = """
                    {
                      \"localId\": \"firebase-local-id\",
                      \"email\": \"admin@local.dev\",
                      \"idToken\": \"id-token-123\",
                      \"refreshToken\": \"refresh-token-123\",
                      \"emailVerified\": true
                    }
                    """;
            byte[] payload = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, payload.length);
            exchange.getResponseBody().write(payload);
            exchange.close();
        });
        server.start();

        FirebasePasswordSignInClient client = newClient(server.getAddress().getPort());

        FirebasePasswordSignInClient.FirebasePasswordSignInResult result = client.signIn("admin@local.dev", "Test1234!");

        assertThat(result.localId()).isEqualTo("firebase-local-id");
        assertThat(result.email()).isEqualTo("admin@local.dev");
        assertThat(result.idToken()).isEqualTo("id-token-123");
        assertThat(result.refreshToken()).isEqualTo("refresh-token-123");
        assertThat(result.emailVerified()).isTrue();
    }

    @Test
    void signIn_reportsDisabledPasswordProviderAsConfigurationError() throws Exception {
        server = errorServer("OPERATION_NOT_ALLOWED");
        server.start();

        FirebasePasswordSignInClient client = newClient(server.getAddress().getPort());

        assertThatThrownBy(() -> client.signIn("admin@local.dev", "Test1234!"))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("FIREBASE_PASSWORD_SIGN_IN_DISABLED");
    }

    @Test
    void signIn_keepsInvalidCredentialsUnauthorizedWithFirebaseDetail() throws Exception {
        server = errorServer("INVALID_LOGIN_CREDENTIALS");
        server.start();

        FirebasePasswordSignInClient client = newClient(server.getAddress().getPort());

        assertThatThrownBy(() -> client.signIn("admin@local.dev", "bad-password"))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> {
                    ApiException apiException = (ApiException) error;
                    assertThat(apiException.getStatus().value()).isEqualTo(401);
                    assertThat(apiException.getCode()).isEqualTo("INVALID_CREDENTIALS");
                    assertThat(apiException.getDetail()).asString().contains("INVALID_LOGIN_CREDENTIALS");
                });
    }

    @Test
    void signIn_reportsProviderNotLinkedWhenPasswordProviderMissing() throws Exception {
        server = errorServer("INVALID_LOGIN_CREDENTIALS");
        server.start();

        FirebaseAuth firebaseAuth = mock(FirebaseAuth.class);
        UserRecord userRecord = mock(UserRecord.class);
        UserInfo googleProvider = mock(UserInfo.class);
        when(googleProvider.getProviderId()).thenReturn("google.com");
        when(userRecord.getProviderData()).thenReturn(new UserInfo[]{googleProvider});
        when(firebaseAuth.getUserByEmail("admin@local.dev")).thenReturn(userRecord);

        FirebasePasswordSignInClient client = newClient(server.getAddress().getPort(), firebaseAuth);

        assertThatThrownBy(() -> client.signIn("admin@local.dev", "bad-password"))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> {
                    ApiException apiException = (ApiException) error;
                    assertThat(apiException.getStatus().value()).isEqualTo(409);
                    assertThat(apiException.getCode()).isEqualTo("ADMIN_PASSWORD_PROVIDER_NOT_LINKED");
                });
    }

    @Test
    void signIn_keepsUnauthorizedWhenPasswordProviderExists() throws Exception {
        server = errorServer("INVALID_LOGIN_CREDENTIALS");
        server.start();

        FirebaseAuth firebaseAuth = mock(FirebaseAuth.class);
        UserRecord userRecord = mock(UserRecord.class);
        UserInfo passwordProvider = mock(UserInfo.class);
        when(passwordProvider.getProviderId()).thenReturn("password");
        when(userRecord.getProviderData()).thenReturn(new UserInfo[]{passwordProvider});
        when(userRecord.isDisabled()).thenReturn(false);
        when(firebaseAuth.getUserByEmail("admin@local.dev")).thenReturn(userRecord);

        FirebasePasswordSignInClient client = newClient(server.getAddress().getPort(), firebaseAuth);

        assertThatThrownBy(() -> client.signIn("admin@local.dev", "bad-password"))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> {
                    ApiException apiException = (ApiException) error;
                    assertThat(apiException.getStatus().value()).isEqualTo(401);
                    assertThat(apiException.getCode()).isEqualTo("INVALID_CREDENTIALS");
                });
    }

    @Test
    void signIn_fallbacksUnauthorizedWhenUserLookupFails() throws Exception {
        server = errorServer("INVALID_LOGIN_CREDENTIALS");
        server.start();

        FirebaseAuth firebaseAuth = mock(FirebaseAuth.class);
        when(firebaseAuth.getUserByEmail("admin@local.dev")).thenThrow(mock(FirebaseAuthException.class));

        FirebasePasswordSignInClient client = newClient(server.getAddress().getPort(), firebaseAuth);

        assertThatThrownBy(() -> client.signIn("admin@local.dev", "bad-password"))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> {
                    ApiException apiException = (ApiException) error;
                    assertThat(apiException.getStatus().value()).isEqualTo(401);
                    assertThat(apiException.getCode()).isEqualTo("INVALID_CREDENTIALS");
                });
    }

    private void stopServer(HttpServer server) {
        try {
            if (server != null) {
                server.stop(0);
            }
        } catch (RuntimeException ignored) {
        }
    }

    private FirebasePasswordSignInClient newClient(int port) {
        return newClient(port, mock(FirebaseAuth.class));
    }

    private FirebasePasswordSignInClient newClient(int port, FirebaseAuth firebaseAuth) {
        FirebaseAuthProperties properties = new FirebaseAuthProperties();
        properties.setMode(FirebaseAuthProperties.Mode.PRODUCTION);
        properties.setWebApiKey("test-api-key");

        return new FirebasePasswordSignInClient(properties, firebaseAuth) {
            @Override
            String resolveBaseUrl() {
                return "http://127.0.0.1:" + port;
            }

            @Override
            String resolveApiKey() {
                return "test-api-key";
            }
        };
    }

    private HttpServer errorServer(String firebaseErrorCode) throws IOException {
        HttpServer errorServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        errorServer.createContext("/v1/accounts:signInWithPassword", exchange -> {
            String response = """
                    {
                      \"error\": {
                        \"message\": \"%s\"
                      }
                    }
                    """.formatted(firebaseErrorCode);
            byte[] payload = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(400, payload.length);
            exchange.getResponseBody().write(payload);
            exchange.close();
        });
        return errorServer;
    }
}
