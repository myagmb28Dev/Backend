package com.example.pogun.dto.shelterpet;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
/**
 * API 요청/응답 데이터 전송 객체인 ShelterPetUpdateResponse이다.
 */

@Schema(description = "외부 공고 수정 응답")
public record ShelterPetUpdateResponse(
        String id,
        boolean updated,
        String title,
        String description,
        Integer rewardAmount,
        String contactPhone,
        List<String> imageUrls
) {
}
