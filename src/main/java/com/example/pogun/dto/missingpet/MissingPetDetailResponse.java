package com.example.pogun.dto.missingpet;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
/**
 * API 요청/응답 데이터 전송 객체인 MissingPetDetailResponse이다.
 */

@Schema(description = "실종 공고 상세 응답")
public record MissingPetDetailResponse(
        UUID id,
        String title,
        String animalType,
        String breed,
        String gender,
        Integer age,
        String color,
        String description,
        Instant missingDate,
        String missingRegion,
        String missingAddress,
        Integer rewardAmount,
        String contactPhone,
        String status,
        String statusLabel,
        boolean statusChanged,
        Long viewCount,
        long bookmarkCount,
        boolean isUrgent,
        Boolean hidden,
        UUID authorId,
        String authorNickname,
        Instant createdAt,
        Instant updatedAt,
        List<String> imageUrls
) {
}

