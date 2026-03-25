package com.example.pogun.dto.missingpet;

import io.swagger.v3.oas.annotations.media.Schema;
/**
 * API 요청/응답 데이터 전송 객체인 MissingPetListFiltersResponse이다.
 */

@Schema(description = "실종 공고 목록 필터 응답")
public record MissingPetListFiltersResponse(
        String region,
        String breed,
        String status,
        String from,
        String to,
        String sort,
        int page,
        int size
) {
}
