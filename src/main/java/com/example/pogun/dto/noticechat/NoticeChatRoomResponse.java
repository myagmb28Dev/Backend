package com.example.pogun.dto.noticechat;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;
/**
 * API 요청/응답 데이터 전송 객체인 NoticeChatRoomResponse이다.
 */

@Schema(description = "채팅방 응답")
public record NoticeChatRoomResponse(
        @Schema(description = "채팅방 ID") UUID roomId,
        @Schema(description = "공고 ID") UUID noticeId,
        @Schema(description = "공고 제목") String noticeTitle,
        @Schema(description = "채팅방 상태") String roomStatus,
        @Schema(description = "최근 메시지 시각") Instant lastMessageAt,
        @Schema(description = "생성 시각") Instant createdAt,
        @Schema(description = "상대방 사용자 ID") UUID opponentUserId,
        @Schema(description = "상대방 닉네임") String opponentNickname,
        @Schema(description = "안 읽은 메시지 수") long unreadCount
) {
}
