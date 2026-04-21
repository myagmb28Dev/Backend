package com.example.pogun.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "사용자 가용 상태 응답")
public record UserAvailabilityResponse(
        @Schema(description = "가용 상태") String availabilityStatus,
        @Schema(description = "마지막 활동 시각") Instant lastActiveAt
) {
}
