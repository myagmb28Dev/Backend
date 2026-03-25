package com.example.pogun.dto.shelterpet;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
/**
 * API 요청/응답 데이터 전송 객체인 ShelterPetListResponse이다.
 */

@Schema(description = "외부 공고 목록 응답")
public record ShelterPetListResponse(
        String source,
        ShelterPetListFiltersResponse filters,
        List<ShelterPetSummaryResponse> items
) {
}
