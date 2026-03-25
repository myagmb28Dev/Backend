package com.example.pogun.dto.report;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
/**
 * API 요청/응답 데이터 전송 객체인 ReportStatusUpdateRequest이다.
 */

@Getter
@Setter
@Schema(description = "신고 상태 변경 요청")
public class ReportStatusUpdateRequest {
    @NotBlank
    @Schema(description = "신고 상태", example = "REVIEWING")
    private String status;
}
