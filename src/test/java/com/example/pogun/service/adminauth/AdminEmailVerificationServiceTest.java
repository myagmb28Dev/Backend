package com.example.pogun.service.adminauth;

import com.example.pogun.config.FirebaseAuthProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AdminEmailVerificationServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final FirebaseAuthProperties firebaseAuthProperties = new FirebaseAuthProperties();
    private HttpServer server;
    private AdminEmailVerificationService service;
    private final AtomicReference<String> capturedBody = new AtomicReference<>();
    private final AtomicReference<String> capturedKey = new AtomicReference<>();

    @BeforeEach
    void setUp() throws Exception {
        firebaseAuthProperties.setMode(FirebaseAuthProperties.Mode.PRODUCTION);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/accounts:sendOobCode", this::handleSendOobCode);
        server.start();
        int port = server.getAddress().getPort();

        service = new AdminEmailVerificationService(firebaseAuthProperties, WebClient.builder()) {
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

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendVerificationEmail_postsFirebaseVerificationRequest() throws Exception {
        service.sendVerificationEmail("id-token-123");

        JsonNode body = OBJECT_MAPPER.readTree(capturedBody.get());
        assertThat(capturedKey.get()).isEqualTo("key=test-api-key");
        assertThat(body.path("requestType").asText()).isEqualTo("VERIFY_EMAIL");
        assertThat(body.path("idToken").asText()).isEqualTo("id-token-123");
    }

    private void handleSendOobCode(HttpExchange exchange) throws IOException {
        capturedKey.set(exchange.getRequestURI().getQuery());
        capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

        byte[] response = "{\"email\":\"admin@example.com\"}".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(response);
        }
    }
}
