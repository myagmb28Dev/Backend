package com.example.pogun.dto.bookmark;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
/**
 * API 요청/응답 데이터 전송 객체인 BookmarkSummaryResponse이다.
 */

@Schema(description = "즐겨찾기 공고 요약 응답")
public record BookmarkSummaryResponse(
        UUID noticeId,
        String title,
        String animalType,
        String breed,
        Instant missingDate,
        String missingRegion,
        String status,
        String statusLabel,
        boolean statusChanged,
        boolean bookmarked,
        Instant bookmarkedAt
) {
}