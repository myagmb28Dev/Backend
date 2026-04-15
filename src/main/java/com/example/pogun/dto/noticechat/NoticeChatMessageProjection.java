package com.example.pogun.dto.noticechat;

import com.example.pogun.entity.noticechat.enums.NoticeChatMessageType;

import java.time.Instant;
import java.util.UUID;

/**
 * 메시지 목록 조회에서 엔티티 전체 로딩을 피하기 위한 읽기 전용 projection이다.
 */
public record NoticeChatMessageProjection(
        UUID id,
        UUID roomId,
        UUID senderUserId,
        String senderNickname,
        String message,
        NoticeChatMessageType messageType,
        Boolean isRead,
        Instant createdAt,
        String clientMessageId,
        Long roomSequence,
        Instant editedAt,
        Instant deletedAt,
        UUID replyMessageId,
        UUID replySenderUserId,
        String replySenderNickname,
        String replyMessage,
        NoticeChatMessageType replyMessageType,
        Instant replyDeletedAt
) {
}
