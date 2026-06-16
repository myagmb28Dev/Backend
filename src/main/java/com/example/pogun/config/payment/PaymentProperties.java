package com.example.pogun.config.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.payment")
public class PaymentProperties {

    private final Apple apple = new Apple();
    private final Google google = new Google();

    public Apple getApple() {
        return apple;
    }

    public Google getGoogle() {
        return google;
    }

    public static class Apple {
        private boolean enabled = true;
        private String keyId;
        private String issuerId;
        private String bundleId;
        private String privateKeyBase64;
        private String environment = "sandbox";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getKeyId() {
            return keyId;
        }

        public void setKeyId(String keyId) {
            this.keyId = keyId;
        }

        public String getIssuerId() {
            return issuerId;
        }

        public void setIssuerId(String issuerId) {
            this.issuerId = issuerId;
        }

        public String getBundleId() {
            return bundleId;
        }

        public void setBundleId(String bundleId) {
            this.bundleId = bundleId;
        }

        public String getPrivateKeyBase64() {
            return privateKeyBase64;
        }

        public void setPrivateKeyBase64(String privateKeyBase64) {
            this.privateKeyBase64 = privateKeyBase64;
        }

        public String getEnvironment() {
            return environment;
        }

        public void setEnvironment(String environment) {
            this.environment = environment;
        }
    }

    public static class Google {
        private boolean enabled = false;
        private String packageName;
        private String serviceAccountBase64;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getPackageName() {
            return packageName;
        }

        public void setPackageName(String packageName) {
            this.packageName = packageName;
        }

        public String getServiceAccountBase64() {
            return serviceAccountBase64;
        }

        public void setServiceAccountBase64(String serviceAccountBase64) {
            this.serviceAccountBase64 = serviceAccountBase64;
        }
    }
}
