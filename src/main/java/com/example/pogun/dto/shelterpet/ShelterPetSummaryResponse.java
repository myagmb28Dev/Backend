package com.example.pogun.dto.shelterpet;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
/**
 * API 요청/응답 데이터 전송 객체인 ShelterPetSummaryResponse이다.
 */

@Schema(description = "외부 공고 요약 응답")
public record ShelterPetSummaryResponse(
        String id,
        String noticeNo,
        String title,
        String region,
        String breed,
        String status,
        String happenPlace,
        String careName,
        String noticeStartDate,
        String noticeEndDate,
        List<String> imageUrls
) {
}

