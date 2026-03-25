package com.example.pogun.dto.missingpet;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
/**
 * API 요청/응답 데이터 전송 객체인 MissingPetSummaryResponse이다.
 */

@Schema(description = "실종 공고 요약 응답")
public record MissingPetSummaryResponse(
        UUID id,
        String title,
        String animalType,
        String breed,
        Instant missingDate,
        String missingRegion,
        String status,
        String statusLabel,
        boolean statusChanged,
        Long viewCount,
        long bookmarkCount,
        boolean isUrgent,
        String authorNickname,
        List<String> imageUrls
) {
}
