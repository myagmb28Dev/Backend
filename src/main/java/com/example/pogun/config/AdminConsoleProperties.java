package com.example.pogun.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.admin.console")
public class AdminConsoleProperties {

    private long sessionTtlSeconds = 60L * 60L * 8L;
    private long bootstrapSessionTtlSeconds = 60L * 15L;
    private long challengeTtlSeconds = 60L * 5L;
    private long promoteStepupTtlSeconds = 60L * 5L;
    private WebAuthn webauthn = new WebAuthn();
    private EmailVerification emailVerification = new EmailVerification();

    @Getter
    @Setter
    public static class WebAuthn {
        private String rpId;
        private String rpName = "Pogun Admin";
        private List<String> allowedOrigins = new ArrayList<>();
        /**
         * Comma-separated origin->rpId pairs.
         * Example:
         * https://pawgen.kro.kr=pawgen.kro.kr
         */
        private String originRpMap = "";

        public Map<String, String> getOriginRpMappings() {
            Map<String, String> mappings = new LinkedHashMap<>();
            if (originRpMap == null || originRpMap.isBlank()) {
                return mappings;
            }
            String[] entries = originRpMap.split(",");
            for (String rawEntry : entries) {
                if (rawEntry == null || rawEntry.isBlank()) {
                    continue;
                }
                int eq = rawEntry.indexOf('=');
                if (eq <= 0 || eq >= rawEntry.length() - 1) {
                    continue;
                }
                String origin = rawEntry.substring(0, eq).trim();
                String rp = rawEntry.substring(eq + 1).trim();
                if (!origin.isBlank() && !rp.isBlank()) {
                    mappings.put(origin, rp);
                }
            }
            return mappings;
        }
    }

    @Getter
    @Setter
    public static class EmailVerification {
        /**
         * Comma-separated origin->continueUrl pairs.
         * Example:
         * https://pawgen.kro.kr=https://pawgen.kro.kr/login?adminVerify=done
         */
        private String continueUrlMap = "";
        private String defaultContinueUrl = "";

        public Map<String, String> getContinueUrlMappings() {
            Map<String, String> mappings = new LinkedHashMap<>();
            if (continueUrlMap == null || continueUrlMap.isBlank()) {
                return mappings;
            }
            String[] entries = continueUrlMap.split(",");
            for (String rawEntry : entries) {
                if (rawEntry == null || rawEntry.isBlank()) {
                    continue;
                }
                int eq = rawEntry.indexOf('=');
                if (eq <= 0 || eq >= rawEntry.length() - 1) {
                    continue;
                }
                String origin = rawEntry.substring(0, eq).trim();
                String url = rawEntry.substring(eq + 1).trim();
                if (!origin.isBlank() && !url.isBlank()) {
                    mappings.put(origin, url);
                }
            }
            return mappings;
        }
    }
}
