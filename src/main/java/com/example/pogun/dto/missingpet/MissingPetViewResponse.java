package com.example.pogun.dto.missingpet;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;
/**
 * API 요청/응답 데이터 전송 객체인 MissingPetViewResponse이다.
 */

@Schema(description = "실종 공고 조회수 응답")
public record MissingPetViewResponse(
        UUID id,
        Long viewCount
) {
}

