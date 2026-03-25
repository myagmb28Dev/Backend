package com.example.pogun.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;
/**
 * API 요청/응답 데이터 전송 객체인 UserPetNoticeSummaryResponse이다.
 */

@Schema(description = "내 실종 공고 요약 응답")
public record UserPetNoticeSummaryResponse(
        @Schema(description = "공고 ID") UUID noticeId,
        @Schema(description = "제목") String title,
        @Schema(description = "동물 종류") String animalType,
        @Schema(description = "품종") String breed,
        @Schema(description = "실종 시각") Instant missingDate,
        @Schema(description = "실종 지역") String missingRegion,
        @Schema(description = "상태") String status,
        @Schema(description = "조회수") Long viewCount,
        @Schema(description = "생성 시각") Instant createdAt
) {
}