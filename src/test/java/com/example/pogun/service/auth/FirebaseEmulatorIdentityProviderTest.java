package com.example.pogun.service.auth;

import com.example.pogun.config.firebase.FirebaseAuthProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FirebaseEmulatorIdentityProviderTest {

    @Test
    void verifyIdToken_parsesEmulatorTokenWhenAllowed() {
        FirebaseAuthProperties properties = new FirebaseAuthProperties();
        properties.setMode(FirebaseAuthProperties.Mode.EMULATOR);
        properties.setAllowEmulator(true);
        properties.setProjectId("pogun-local");

        FirebaseEmulatorIdentityProvider provider = new FirebaseEmulatorIdentityProvider(new ObjectMapper(), properties);
        String token = tokenFor("pogun-local", "emulator-uid", "tester@local.dev");

        FirebaseIdentityService.FirebaseIdentity identity = provider.verifyIdToken(token, false);

        assertThat(identity.uid()).isEqualTo("emulator-uid");
        assertThat(identity.email()).isEqualTo("tester@local.dev");
        assertThat(identity.signInProvider()).isEqualTo("password");
    }

    @Test
    void verifyIdToken_rejectsTokenWhenEmulatorNotAllowed() {
        FirebaseAuthProperties properties = new FirebaseAuthProperties();
        properties.setMode(FirebaseAuthProperties.Mode.EMULATOR);
        properties.setAllowEmulator(false);
        properties.setProjectId("pogun-local");

        FirebaseEmulatorIdentityProvider provider = new FirebaseEmulatorIdentityProvider(new ObjectMapper(), properties);

        assertThatThrownBy(() -> provider.verifyIdToken(tokenFor("pogun-local", "uid", "tester@local.dev"), true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("허용되지 않았습니다");
    }

    @Test
    void verifyIdToken_rejectsMismatchedAudience() {
        FirebaseAuthProperties properties = new FirebaseAuthProperties();
        properties.setMode(FirebaseAuthProperties.Mode.EMULATOR);
        properties.setAllowEmulator(true);
        properties.setProjectId("pogun-local");

        FirebaseEmulatorIdentityProvider provider = new FirebaseEmulatorIdentityProvider(new ObjectMapper(), properties);

        assertThatThrownBy(() -> provider.verifyIdToken(tokenFor("other-project", "uid", "tester@local.dev"), true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("프로젝트");
    }

    private String tokenFor(String projectId, String uid, String email) {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("""
                        {
                          "aud":"%s",
                          "iss":"https://securetoken.google.com/%s",
                          "user_id":"%s",
                          "sub":"%s",
                          "email":"%s",
                          "name":"Local Tester",
                          "firebase":{"sign_in_provider":"password"}
                        }
                        """.formatted(projectId, projectId, uid, uid, email)).getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + ".";
    }
}
