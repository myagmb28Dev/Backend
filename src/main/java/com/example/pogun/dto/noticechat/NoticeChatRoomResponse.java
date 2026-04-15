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
        @Schema(description = "공고 대표 이미지 URL") String noticeImageUrl,
        @Schema(description = "공고 상태") String noticeStatus,
        @Schema(description = "공고 상태 라벨") String noticeStatusLabel,
        @Schema(description = "공고 실종 지역") String noticeMissingRegion,
        @Schema(description = "채팅방 상태") String roomStatus,
        @Schema(description = "최근 메시지 유형") String lastMessageType,
        @Schema(description = "최근 메시지 시각") Instant lastMessageAt,
        @Schema(description = "최근 메시지 미리보기") String lastMessagePreview,
        @Schema(description = "생성 시각") Instant createdAt,
        @Schema(description = "상대방 사용자 ID") UUID opponentUserId,
        @Schema(description = "상대방 닉네임") String opponentNickname,
        @Schema(description = "안 읽은 메시지 수") long unreadCount,
        @Schema(description = "알림 사용 여부") Boolean notificationEnabled,
        @Schema(description = "즐겨찾기 여부") Boolean favorite,
        @Schema(description = "상단 고정 여부") Boolean pinned,
        @Schema(description = "채팅방 나간 시각") Instant leftAt,
        @Schema(description = "상대방 온라인 여부") Boolean opponentOnline,
        @Schema(description = "상대방 마지막 활동 시각") Instant opponentLastActiveAt,
        @Schema(description = "마지막으로 읽은 메시지 ID") UUID lastReadMessageId,
        @Schema(description = "마지막 읽음 시각") Instant lastReadAt,
        @Schema(description = "마지막으로 읽은 채팅방 순번") Long lastReadRoomSequence
) {
}
