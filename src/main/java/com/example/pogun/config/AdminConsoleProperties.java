package com.example.pogun.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.admin.console")
public class AdminConsoleProperties {

    private long sessionTtlSeconds = 60L * 60L * 8L;
    private long bootstrapSessionTtlSeconds = 60L * 15L;
    private long challengeTtlSeconds = 60L * 5L;
    private WebAuthn webauthn = new WebAuthn();

    @Getter
    @Setter
    public static class WebAuthn {
        private String rpId;
        private String rpName = "Pogun Admin";
        private List<String> allowedOrigins = new ArrayList<>();
    }
}
