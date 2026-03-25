package com.example.pogun.dto.community;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;
/**
 * API 요청/응답 데이터 전송 객체인 CommunityPostUpdateResponse이다.
 */

@Schema(description = "커뮤니티 글 수정 응답")
public record CommunityPostUpdateResponse(
        UUID id,
        boolean updated,
        String category,
        List<String> tags
) {
}
