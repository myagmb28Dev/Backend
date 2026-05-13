package com.example.pogun.dto.ai;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "유사 공고 항목")
public record SimilarNoticeItemResponse(
        String noticeId,
        String noticeType,
        String detailPath
) {
}

