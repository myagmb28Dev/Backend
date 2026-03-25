package com.example.pogun.config;

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
/**
 * 애플리케이션 설정을 담당하는 FirebaseConfig이다.
 */

@Configuration
public class FirebaseConfig {

    @Value("${FIREBASE_KEY_BASE64:}")
    private String base64Key;

    @Value("${FIREBASE_CONFIG_JSON:}")
    private String firebaseConfigJson;

    @Value("${FIREBASE_AUTH_EMULATOR_HOST:}")
    private String authEmulatorHost;

    @Value("${FIREBASE_ALLOW_AUTH_EMULATOR:false}")
    private boolean allowAuthEmulator;

    @Value("${FIREBASE_PROJECT_ID:${GCLOUD_PROJECT:}}")
    private String firebaseProjectId;

    @Bean
    public FirebaseApp firebaseApp() throws IOException {
        FirebaseOptions.Builder optionsBuilder = FirebaseOptions.builder();
        boolean emulatorMode = authEmulatorHost != null && !authEmulatorHost.isBlank();

        // 실수로 운영/개발 서버가 로컬 Auth Emulator를 물지 않도록 명시적 opt-in을 강제한다.
        if (emulatorMode && !allowAuthEmulator) {
            throw new IOException("Auth Emulator는 명시적으로 허용한 환경에서만 사용할 수 있습니다. 'FIREBASE_ALLOW_AUTH_EMULATOR=true'를 설정하세요.");
        }

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

        // 테스트나 재기동 시 FirebaseApp이 중복 초기화되지 않도록 기존 인스턴스를 재사용한다.
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
