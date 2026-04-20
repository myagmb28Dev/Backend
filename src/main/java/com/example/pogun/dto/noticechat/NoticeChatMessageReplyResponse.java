package com.example.pogun.dto.noticechat;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * 답장 대상 메시지 요약 DTO이다.
 */
@Schema(description = "답장 메시지 요약")
public record NoticeChatMessageReplyResponse(
        @Schema(description = "원본 메시지 ID") UUID messageId,
        @Schema(description = "원본 발신자 사용자 ID") UUID senderUserId,
        @Schema(description = "원본 발신자 닉네임") String senderNickname,
        @Schema(description = "원본 메시지 미리보기") String preview,
        @Schema(description = "원본 메시지 유형") String messageType,
        @Schema(description = "답장 썸네일 URL") String thumbnailUrl
) {
}
