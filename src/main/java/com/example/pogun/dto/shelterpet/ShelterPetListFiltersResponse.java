package com.example.pogun.dto.shelterpet;

import io.swagger.v3.oas.annotations.media.Schema;
/**
 * API 요청/응답 데이터 전송 객체인 ShelterPetListFiltersResponse이다.
 */

@Schema(description = "외부 공고 목록 필터 응답")
public record ShelterPetListFiltersResponse(
        String region,
        String breed,
        String status,
        String sort,
        int page,
        int size
) {
}

