package com.example.pogun.dto.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;
/**
 * API 요청/응답 데이터 전송 객체인 AiAnalysisRequest이다.
 */

@Getter
@Setter
@Schema(description = "사진 분석 요청")
public class AiAnalysisRequest {
    @NotBlank
    @Schema(description = "업로드된 사진 ID", example = "photo-1")
    private String photoId;

    public Map<String, Object> toRequestMap() {
        return Map.of("photoId", photoId);
    }
}
