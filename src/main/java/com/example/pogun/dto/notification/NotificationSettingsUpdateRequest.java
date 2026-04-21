package com.example.pogun.dto.notification;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Schema(description = "알림 설정 변경 요청")
public class NotificationSettingsUpdateRequest {
    @Valid
    @NotEmpty
    private List<NotificationSettingItemRequest> settings;
}
