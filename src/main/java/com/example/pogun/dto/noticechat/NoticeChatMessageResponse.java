package com.example.pogun.dto.noticechat;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;
/**
 * API 요청/응답 데이터 전송 객체인 NoticeChatMessageResponse이다.
 */

@Schema(description = "채팅 메시지 응답")
public record NoticeChatMessageResponse(
        @Schema(description = "메시지 ID") UUID id,
        @Schema(description = "채팅방 ID") UUID roomId,
        @Schema(description = "발신자 사용자 ID") UUID senderUserId,
        @Schema(description = "발신자 닉네임") String senderNickname,
        @Schema(description = "메시지 내용") String message,
        @Schema(description = "읽음 여부") Boolean isRead,
        @Schema(description = "내 메시지 여부") boolean mine,
        @Schema(description = "생성 시각") Instant createdAt
) {
}