package com.example.pogun.dto.user;

import java.util.List;

public record CreditLedgerListResponse(
        int count,
        List<CreditLedgerItemResponse> items
) {
}
