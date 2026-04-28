package com.example.pogun.dto.admin.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "관리자 Google 로그인 요청")
public class AdminLoginRequest {

    @NotBlank(message = "firebaseIdToken은 필수입니다.")
    private String firebaseIdToken;
}
