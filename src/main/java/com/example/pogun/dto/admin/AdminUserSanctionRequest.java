package com.example.pogun.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
/**
 * API 요청/응답 데이터 전송 객체인 AdminUserSanctionRequest이다.
 */

@Getter
@Setter
@Schema(description = "관리자 사용자 제재 요청")
public class AdminUserSanctionRequest {
    @NotBlank
    @Schema(description = "제재 액션", example = "BAN")
    private String action;
}