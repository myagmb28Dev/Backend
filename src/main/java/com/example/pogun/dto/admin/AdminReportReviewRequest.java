package com.example.pogun.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "관리자 신고 검토 요청")
public class AdminReportReviewRequest {
    @NotBlank
    @Schema(description = "신고 상태", example = "RESOLVED")
    private String status;

    @Schema(description = "처리 사유", example = "허위 공고로 판단되어 숨김 처리했습니다.")
    private String processReason;

    @Schema(description = "처리 액션", example = "HIDE_TARGET")
    private String processAction;
}
