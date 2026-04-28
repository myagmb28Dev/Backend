package com.example.pogun.config;

import com.example.pogun.config.AdminConsoleProperties;
import com.example.pogun.service.auth.FirebaseAdminIdentityProvider;
import com.example.pogun.service.auth.FirebaseEmulatorIdentityProvider;
import com.example.pogun.service.auth.FirebaseIdentityProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.messaging.FirebaseMessaging;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Date;
/**
 * 애플리케이션 설정을 담당하는 FirebaseConfig이다.
 */

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties({FirebaseAuthProperties.class, AdminConsoleProperties.class})
public class FirebaseConfig {

    private final FirebaseAuthProperties firebaseAuthProperties;

    @Value("${FIREBASE_KEY_BASE64:}")
    private String base64Key;

    @Value("${FIREBASE_CONFIG_JSON:}")
    private String firebaseConfigJson;

    @Bean
    public FirebaseApp firebaseApp() throws IOException {
        FirebaseOptions.Builder optionsBuilder = FirebaseOptions.builder();
        boolean emulatorMode = firebaseAuthProperties.isEmulatorMode();

        if (emulatorMode && !firebaseAuthProperties.isAllowEmulator()) {
            throw new IOException("Auth Emulator는 명시적으로 허용한 테스트 환경에서만 사용할 수 있습니다. 'FIREBASE_ALLOW_AUTH_EMULATOR=true'를 설정하세요.");
        }

        if (emulatorMode) {
            optionsBuilder.setCredentials(GoogleCredentials.create(
                    new com.google.auth.oauth2.AccessToken(
                            "emulator-access-token",
                            Date.from(Instant.now().plusSeconds(3600))
                    )));
        } else if (base64Key != null && !base64Key.isBlank()) {
            byte[] decodedBytes = java.util.Base64.getDecoder().decode(base64Key);
            try (InputStream serviceAccount = new java.io.ByteArrayInputStream(decodedBytes)) {
                optionsBuilder.setCredentials(GoogleCredentials.fromStream(serviceAccount));
            }
        } else if (firebaseConfigJson != null && !firebaseConfigJson.isBlank()) {
            try (InputStream serviceAccount = new java.io.ByteArrayInputStream(firebaseConfigJson.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
                optionsBuilder.setCredentials(GoogleCredentials.fromStream(serviceAccount));
            }
        } else {
            throw new IOException("Firebase 인증 정보를 찾을 수 없습니다. 운영 환경에서는 'FIREBASE_KEY_BASE64' 또는 'FIREBASE_CONFIG_JSON'이 필요합니다.");
        }

        if (firebaseAuthProperties.getProjectId() != null && !firebaseAuthProperties.getProjectId().isBlank()) {
            optionsBuilder.setProjectId(firebaseAuthProperties.getProjectId().trim());
        } else if (emulatorMode) {
            throw new IOException("Auth Emulator 사용 시 'app.firebase.auth.project-id' 또는 'FIREBASE_PROJECT_ID'가 필요합니다.");
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
    public FirebaseIdentityProvider firebaseIdentityProvider(
            ObjectMapper objectMapper,
            FirebaseAuth firebaseAuth
    ) {
        if (firebaseAuthProperties.isEmulatorMode()) {
            return new FirebaseEmulatorIdentityProvider(objectMapper, firebaseAuthProperties);
        }
        return new FirebaseAdminIdentityProvider(firebaseAuth);
    }

    @Bean
    public FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
        return FirebaseMessaging.getInstance(firebaseApp);
    }
}

