package com.example.pogun.dto.user;

import java.time.Instant;
import java.util.UUID;

public record CreditLedgerItemResponse(
        UUID id,
        String type,
        Integer delta,
        Integer balanceBefore,
        Integer balanceAfter,
        String referenceType,
        String referenceId,
        String description,
        Instant createdAt
) {
}
