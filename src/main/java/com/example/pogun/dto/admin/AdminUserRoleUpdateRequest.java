package com.example.pogun.dto.admin;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminUserRoleUpdateRequest {
    @NotBlank(message = "role은 필수입니다.")
    private String role;
}
