package com.example.pogun.dto.notification;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "알림 설정 항목 변경 요청")
public class NotificationSettingItemRequest {
    @NotBlank
    private String type;

    @NotNull
    private Boolean enabled;
}
