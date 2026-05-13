package com.example.pogun.dto.ai;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "유사 공고 조회 응답")
public record SimilarNoticeListResponse(
        String targetType,
        String targetId,
        int count,
        List<SimilarNoticeItemResponse> items
) {
}

