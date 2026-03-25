package com.example.pogun.dto.community;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
/**
 * API 요청/응답 데이터 전송 객체인 CommunityPollResponse이다.
 */

@Schema(description = "커뮤니티 투표 응답")
public record CommunityPollResponse(
        String question,
        List<String> options
) {
}

