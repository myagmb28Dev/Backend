package com.example.pogun.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
/**
 * API 요청/응답 데이터 전송 객체인 AdminVisibilityUpdateRequest이다.
 */

@Getter
@Setter
@Schema(description = "관리자 공개 상태 변경 요청")
public class AdminVisibilityUpdateRequest {
    @NotBlank
    @Schema(description = "공개 상태", example = "VISIBLE")
    private String visibility;
}