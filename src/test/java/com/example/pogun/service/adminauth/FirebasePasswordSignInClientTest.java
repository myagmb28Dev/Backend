package com.example.pogun.service.adminauth;

import com.example.pogun.config.FirebaseAuthProperties;
import com.google.firebase.auth.FirebaseAuth;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class FirebasePasswordSignInClientTest {

    @Test
    void signIn_parsesJsonResponseFromHttpBody() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
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

        try {
            int port = server.getAddress().getPort();
            FirebaseAuthProperties properties = new FirebaseAuthProperties();
            properties.setMode(FirebaseAuthProperties.Mode.PRODUCTION);
            properties.setWebApiKey("test-api-key");

            FirebasePasswordSignInClient client = new FirebasePasswordSignInClient(properties, mock(FirebaseAuth.class)) {
                @Override
                String resolveBaseUrl() {
                    return "http://127.0.0.1:" + port;
                }

                @Override
                String resolveApiKey() {
                    return "test-api-key";
                }
            };

            FirebasePasswordSignInClient.FirebasePasswordSignInResult result = client.signIn("admin@local.dev", "Test1234!");

            assertThat(result.localId()).isEqualTo("firebase-local-id");
            assertThat(result.email()).isEqualTo("admin@local.dev");
            assertThat(result.idToken()).isEqualTo("id-token-123");
            assertThat(result.refreshToken()).isEqualTo("refresh-token-123");
            assertThat(result.emailVerified()).isTrue();
        } finally {
            stopServer(server);
        }
    }

    private void stopServer(HttpServer server) {
        try {
            server.stop(0);
        } catch (RuntimeException ignored) {
        }
    }
}
