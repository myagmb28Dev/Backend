package com.example.pogun.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "사용자 가용 상태 응답")
public record UserAvailabilityResponse(
        @Schema(description = "선택한 수동 상태(하위 호환 필드)") String availabilityStatus,
        @Schema(description = "사용자 수동 상태") String manualPresenceStatus,
        @Schema(description = "최종 표시 상태") String effectivePresenceStatus,
        @Schema(description = "실제 연결 상태", example = "connected") String actualConnectionState,
        @Schema(description = "마지막 활동 시각") Instant lastActiveAt
) {
}
