package com.example.pogun.dto.shelterpet;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
/**
 * API 요청/응답 데이터 전송 객체인 ShelterReferenceListResponse이다.
 */
@Schema(description = "보호소 보조 조회 목록 응답")
public record ShelterReferenceListResponse(
        @Schema(description = "데이터 출처") String source,
        @Schema(description = "조회 타입") String type,
        @Schema(description = "상위 코드") String parentCode,
        @Schema(description = "목록") List<ShelterReferenceItemResponse> items
) {
}
