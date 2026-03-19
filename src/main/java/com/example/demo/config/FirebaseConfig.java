package com.example.demo.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.messaging.FirebaseMessaging;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;

@Configuration
public class FirebaseConfig {

    @Value("${FIREBASE_KEY_BASE64:}")
    private String base64Key;

    @Value("${FIREBASE_CONFIG_JSON:}")
    private String firebaseConfigJson;

    @Bean
    public FirebaseApp firebaseApp() throws IOException {
        InputStream serviceAccount;

        if (base64Key != null && !base64Key.isBlank()) {
            byte[] decodedBytes = java.util.Base64.getDecoder().decode(base64Key);
            serviceAccount = new java.io.ByteArrayInputStream(decodedBytes);
        } else if (firebaseConfigJson != null && !firebaseConfigJson.isBlank()) {
            serviceAccount = new java.io.ByteArrayInputStream(firebaseConfigJson.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } else {
            throw new IOException("Firebase 인증 정보를 찾을 수 없습니다. (환경변수 'FIREBASE_KEY_BASE64' 또는 'FIREBASE_CONFIG_JSON'이 누락됨)");
        }

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                .build();

        if (FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.initializeApp(options);
        } else {
            return FirebaseApp.getInstance();
        }
    }

    @Bean
    public FirebaseAuth firebaseAuth(FirebaseApp firebaseApp) {
        return FirebaseAuth.getInstance(firebaseApp);
    }

    @Bean
    public FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
        return FirebaseMessaging.getInstance(firebaseApp);
    }
}
