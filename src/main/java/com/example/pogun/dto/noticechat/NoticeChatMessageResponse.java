package com.example.pogun.dto.noticechat;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
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
        @Schema(description = "발신자 프로필 이미지 URL") String senderProfileImageUrl,
        @Schema(description = "메시지 내용") String message,
        @Schema(description = "메시지 유형") String messageType,
        @Schema(description = "첨부 이미지 목록") List<NoticeChatMessageImageResponse> images,
        @Schema(description = "답장 메시지 요약") NoticeChatMessageReplyResponse reply,
        @Schema(description = "읽음 여부") Boolean isRead,
        @Schema(description = "내 메시지 여부") boolean mine,
        @Schema(description = "생성 시각") Instant createdAt,
        @Schema(description = "클라이언트 메시지 ID") String clientMessageId,
        @Schema(description = "채팅방 내 서버 순번") Long roomSequence,
        @Schema(description = "서버 수신 시각") Instant serverReceivedAt,
        @Schema(description = "수정 시각") Instant editedAt,
        @Schema(description = "삭제 시각") Instant deletedAt,
        @Schema(description = "삭제 여부") boolean deleted
) {
}
