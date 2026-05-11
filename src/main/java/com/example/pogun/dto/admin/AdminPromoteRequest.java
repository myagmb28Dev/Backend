package com.example.pogun.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * API 요청/응답 데이터 전송 객체인 AdminPromoteRequest이다.
 */
@Getter
@Setter
@Schema(description = "관리자 승격 요청")
public class AdminPromoteRequest {

    @NotBlank(message = "userId는 필수입니다.")
    @Schema(description = "관리자로 승격할 사용자 ID", example = "2de13643-f46b-48c0-8d48-8139d17fea32")
    private String userId;
}
