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
        @Schema(description = "최근 메시지 유형") String lastMessageType,
        @Schema(description = "최근 메시지 시각") Instant lastMessageAt,
        @Schema(description = "최근 메시지 미리보기") String lastMessagePreview,
        @Schema(description = "생성 시각") Instant createdAt,
        @Schema(description = "상대방 사용자 ID") UUID opponentUserId,
        @Schema(description = "상대방 닉네임") String opponentNickname,
        @Schema(description = "상대방 프로필 이미지 URL") String opponentProfileImageUrl,
        @Schema(description = "안 읽은 메시지 수") long unreadCount,
        @Schema(description = "알림 사용 여부") Boolean notificationEnabled,
        @Schema(description = "즐겨찾기 여부") Boolean favorite,
        @Schema(description = "상단 고정 여부") Boolean pinned,
        @Schema(description = "채팅방 나간 시각") Instant leftAt,
        @Schema(description = "상대방 온라인 여부") Boolean opponentOnline,
        @Schema(description = "상대방 수동 상태") String opponentManualPresenceStatus,
        @Schema(description = "상대방 실제 연결 상태", example = "connected") String opponentConnectionState,
        @Schema(description = "상대방 최종 표시 상태") String opponentEffectivePresenceStatus,
        @Schema(description = "상대방 가용 상태") String opponentAvailability,
        @Schema(description = "상대방 마지막 활동 시각") Instant opponentLastActiveAt,
        @Schema(description = "마지막으로 읽은 메시지 ID") UUID lastReadMessageId,
        @Schema(description = "마지막 읽음 시각") Instant lastReadAt,
        @Schema(description = "마지막으로 읽은 방 순번") Long lastReadRoomSequence,
        @Schema(description = "방의 마지막 메시지 순번") Long lastMessageSequence,
        @Schema(description = "공고 대표 이미지 URL") String noticeThumbnailUrl,
        @Schema(description = "최종 표시 채팅방 이름") String displayRoomName,
        @Schema(description = "최종 표시 썸네일 URL") String displayThumbnailUrl,
        @Schema(description = "사용자별 채팅방 표시 이름") String customRoomName,
        @Schema(description = "사용자별 채팅방 썸네일 URL") String customThumbnailUrl
) {
}
