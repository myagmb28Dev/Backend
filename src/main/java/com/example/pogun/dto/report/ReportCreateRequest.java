package com.example.pogun.dto.report;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;
/**
 * API 요청/응답 데이터 전송 객체인 ReportCreateRequest이다.
 */

@Getter
@Setter
@Schema(description = "신고 생성 요청")
public class ReportCreateRequest {
    @NotBlank
    @Schema(description = "신고 대상 타입", example = "PET_NOTICE")
    private String targetType;

    @NotBlank
    @Schema(description = "신고 대상 ID")
    private String targetId;

    @Schema(description = "신고 사유", example = "FALSE_INFORMATION")
    private String reason;

    @Schema(description = "상세 설명", example = "허위 게시물로 보입니다.")
    private String description;

    public Map<String, Object> toRequestMap() {
        Map<String, Object> request = new LinkedHashMap<>();
        putIfNotNull(request, "targetType", targetType);
        putIfNotNull(request, "targetId", targetId);
        putIfNotNull(request, "reason", reason);
        putIfNotNull(request, "description", description);
        return request;
    }

    private void putIfNotNull(Map<String, Object> request, String key, Object value) {
        if (value != null) {
            request.put(key, value);
        }
    }
}