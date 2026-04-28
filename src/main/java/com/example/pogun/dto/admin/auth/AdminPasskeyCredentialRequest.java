package com.example.pogun.dto.admin.auth;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class AdminPasskeyCredentialRequest {

    @NotNull(message = "challengeId는 필수입니다.")
    private UUID challengeId;

    @NotNull(message = "credential은 필수입니다.")
    private Object credential;
}
