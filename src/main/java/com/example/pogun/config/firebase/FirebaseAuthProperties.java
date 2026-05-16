package com.example.pogun.config.firebase;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.firebase.auth")
public class FirebaseAuthProperties {

    private Mode mode = Mode.PRODUCTION;
    private String projectId;
    private String emulatorProjectId;
    private boolean allowEmulator = false;
    private String webApiKey;

    public boolean isEmulatorMode() {
        return mode == Mode.EMULATOR;
    }

    public enum Mode {
        PRODUCTION,
        EMULATOR
    }
}
