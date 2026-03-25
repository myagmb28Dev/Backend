package com.example.pogun.dto.shelterpet;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
/**
 * API 요청/응답 데이터 전송 객체인 ShelterPetImageAnalysisDetailResponse이다.
 */

@Schema(description = "외부 공고 이미지 분석 상세 응답")
public record ShelterPetImageAnalysisDetailResponse(
        String noticeId,
        String analysisStatus,
        List<String> features,
        List<String> similarNotices
) {
}

