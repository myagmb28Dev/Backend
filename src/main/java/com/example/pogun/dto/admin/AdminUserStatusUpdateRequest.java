package com.example.pogun.dto.admin;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminUserStatusUpdateRequest {
    @NotBlank(message = "status는 필수입니다.")
    private String status;
}
