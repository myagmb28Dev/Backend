package com.example.pogun.dto.user;

import java.time.Instant;

public record UserCreditBalanceResponse(
        Integer balance,
        Instant updatedAt
) {
}
