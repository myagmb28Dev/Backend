package com.example.pogun.dto.missingpet;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
/**
 * API 요청/응답 데이터 전송 객체인 MissingPetListResponse이다.
 */

@Schema(description = "실종 공고 목록 응답")
public record MissingPetListResponse(
        MissingPetListFiltersResponse filters,
        int totalElements,
        int totalPages,
        List<MissingPetSummaryResponse> items
) {
}
