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

    @Value("${FIREBASE_AUTH_EMULATOR_HOST:}")
    private String authEmulatorHost;

    @Value("${FIREBASE_PROJECT_ID:${GCLOUD_PROJECT:}}")
    private String firebaseProjectId;

    @Bean
    public FirebaseApp firebaseApp() throws IOException {
        FirebaseOptions.Builder optionsBuilder = FirebaseOptions.builder();
        boolean emulatorMode = authEmulatorHost != null && !authEmulatorHost.isBlank();

        if (base64Key != null && !base64Key.isBlank()) {
            byte[] decodedBytes = java.util.Base64.getDecoder().decode(base64Key);
            try (InputStream serviceAccount = new java.io.ByteArrayInputStream(decodedBytes)) {
                optionsBuilder.setCredentials(GoogleCredentials.fromStream(serviceAccount));
            }
        } else if (firebaseConfigJson != null && !firebaseConfigJson.isBlank()) {
            try (InputStream serviceAccount = new java.io.ByteArrayInputStream(firebaseConfigJson.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
                optionsBuilder.setCredentials(GoogleCredentials.fromStream(serviceAccount));
            }
        } else if (!emulatorMode) {
            throw new IOException("Firebase 인증 정보를 찾을 수 없습니다. 운영 환경에서는 'FIREBASE_KEY_BASE64' 또는 'FIREBASE_CONFIG_JSON'이 필요합니다.");
        }

        if (firebaseProjectId != null && !firebaseProjectId.isBlank()) {
            optionsBuilder.setProjectId(firebaseProjectId.trim());
        } else if (emulatorMode) {
            throw new IOException("Auth Emulator 사용 시 'FIREBASE_PROJECT_ID' 또는 'GCLOUD_PROJECT' 환경변수가 필요합니다.");
        }

        FirebaseOptions options = optionsBuilder.build();

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
