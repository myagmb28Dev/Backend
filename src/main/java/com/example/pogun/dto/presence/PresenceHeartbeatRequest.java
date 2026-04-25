package com.example.pogun.dto.presence;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PresenceHeartbeatRequest {
    @NotBlank(message = "clientSessionId는 필수입니다.")
    private String clientSessionId;

    private String page;
}
