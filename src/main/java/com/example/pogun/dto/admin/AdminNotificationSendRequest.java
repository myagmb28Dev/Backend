package com.example.pogun.dto.admin;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class AdminNotificationSendRequest {
    @NotBlank(message = "target은 필수입니다.")
    private String target;

    @NotBlank(message = "title은 필수입니다.")
    private String title;

    @NotBlank(message = "body는 필수입니다.")
    private String body;

    private List<String> userIds;
}
