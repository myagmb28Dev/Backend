package com.example.pogun.dto.presence;

import java.time.Instant;

public record PresenceHeartbeatResponse(
        String manualPresenceStatus,
        String globalConnectionState,
        String effectivePresenceStatus,
        Instant lastActiveAt
) {
}
