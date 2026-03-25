package com.example.pogun.dto.shelterpet;

import io.swagger.v3.oas.annotations.media.Schema;
/**
 * API 요청/응답 데이터 전송 객체인 ShelterPetViewResponse이다.
 */

@Schema(description = "외부 공고 조회수 응답")
public record ShelterPetViewResponse(
        String id,
        int viewCount
) {
}

