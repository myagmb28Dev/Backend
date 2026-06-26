package com.example.pogun.dto.payment;

import jakarta.validation.constraints.NotBlank;

public class AppleAppStoreNotificationRequest {

    @NotBlank
    private String signedPayload;

    public String getSignedPayload() {
        return signedPayload;
    }

    public void setSignedPayload(String signedPayload) {
        this.signedPayload = signedPayload;
    }
}
