package com.example.pogun.dto.noticechat;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "채팅 메시지 페이지 응답")
public record NoticeChatMessagePageResponse(
        @Schema(description = "메시지 목록") List<NoticeChatMessageResponse> messages,
        @Schema(description = "이전 페이지 존재 여부") boolean hasMore,
        @Schema(description = "다음 이전 페이지 조회 기준 roomSequence") Long nextBeforeSequence,
        @Schema(description = "요청 limit") int limit
) {
}
