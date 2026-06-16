package com.example.pogun.service.payment;

import com.example.pogun.dto.common.ApiResponse.ApiException;

import java.util.Arrays;

public enum PaymentProductCatalog {
    PAW_AI_CREDITS_3_300("paw_ai_credits_3_300", 3, 300, true),
    PAW_AI_CREDITS_5_500("paw_ai_credits_5_500", 5, 500, true),
    PAW_AI_CREDITS_10_500("paw_ai_credits_10_500", 10, 500, true);

    private final String productId;
    private final Integer credits;
    private final Integer maxPromptLength;
    private final boolean supported;

    PaymentProductCatalog(String productId, Integer credits, Integer maxPromptLength, boolean supported) {
        this.productId = productId;
        this.credits = credits;
        this.maxPromptLength = maxPromptLength;
        this.supported = supported;
    }

    public String getProductId() {
        return productId;
    }

    public Integer getCredits() {
        return credits;
    }

    public Integer getMaxPromptLength() {
        return maxPromptLength;
    }

    public static PaymentProductCatalog requireSupported(String productId) {
        PaymentProductCatalog catalog = Arrays.stream(values())
                .filter(item -> item.productId.equals(productId))
                .findFirst()
                .orElseThrow(() -> ApiException.badRequest("INVALID_PRODUCT_ID", "지원하지 않는 상품 ID입니다."));
        if (!catalog.supported || catalog.credits == null) {
            throw ApiException.conflict("PRODUCT_ID_NOT_CONFIRMED", "상품 ID는 존재하지만 적립 크레딧이 아직 확정되지 않았습니다.");
        }
        return catalog;
    }
}
