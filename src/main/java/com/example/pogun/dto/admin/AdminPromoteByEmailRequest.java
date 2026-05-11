package com.example.pogun.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * API 요청/응답 데이터 전송 객체인 AdminPromoteByEmailRequest이다.
 */
@Getter
@Setter
@Schema(description = "관리자 승격 요청(이메일)")
public class AdminPromoteByEmailRequest {

    @NotBlank(message = "email은 필수입니다.")
    @Email(message = "올바른 이메일 형식이 아닙니다.")
    @Schema(description = "관리자로 승격할 이메일", example = "admin@example.com")
    private String email;
}
