package com.example.pogun.dto.shelterpet;

import io.swagger.v3.oas.annotations.media.Schema;
/**
 * API 요청/응답 데이터 전송 객체인 ShelterReferenceItemResponse이다.
 */
@Schema(description = "보호소 보조 조회 항목")
public record ShelterReferenceItemResponse(
        @Schema(description = "코드") String code,
        @Schema(description = "이름") String name
) {
}
