package com.example.pogun.dto.admin.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "로컬 테스트 관리자 로그인 요청")
public class AdminLocalLoginRequest {

    @Schema(description = "로컬 테스트 관리자 이메일", example = "playwright-user1@local.dev")
    private String localTestEmail;

    @Schema(description = "기존 패스키 제거 후 재등록 강제 여부", example = "true")
    private Boolean forcePasskeyEnroll;
}
