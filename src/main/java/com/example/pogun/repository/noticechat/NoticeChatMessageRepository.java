package com.example.pogun.repository.noticechat;

import com.example.pogun.dto.noticechat.NoticeChatMessageProjection;
import com.example.pogun.dto.noticechat.NoticeChatRoomUnreadCountProjection;
import com.example.pogun.entity.noticechat.NoticeChatMessage;
import com.example.pogun.entity.noticechat.NoticeChatRoom;
import com.example.pogun.entity.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
/**
 * 영속성 조회와 저장을 담당하는 NoticeChatMessageRepository이다.
 */

@Repository
public interface NoticeChatMessageRepository extends JpaRepository<NoticeChatMessage, UUID> {
    List<NoticeChatMessage> findByRoomAndDeletedAtIsNullOrderByRoomSequenceAscCreatedAtAsc(NoticeChatRoom room);

    List<NoticeChatMessage> findByRoomAndDeletedAtIsNullAndMessageContainingIgnoreCaseOrderByRoomSequenceAscCreatedAtAsc(NoticeChatRoom room, String message);

    Optional<NoticeChatMessage> findTopByRoomAndDeletedAtIsNullOrderByCreatedAtDesc(NoticeChatRoom room);

    Optional<NoticeChatMessage> findTopByRoomAndDeletedAtIsNullOrderByRoomSequenceDescCreatedAtDesc(NoticeChatRoom room);

    List<NoticeChatMessage> findByDeletedAtBefore(Instant cutoff);

    Optional<NoticeChatMessage> findTopByRoomAndSenderUserNotAndDeletedAtIsNullOrderByRoomSequenceDescCreatedAtDesc(NoticeChatRoom room, User senderUser);

    Optional<NoticeChatMessage> findTopByRoomAndSenderUserNotAndDeletedAtIsNullAndRoomSequenceLessThanEqualOrderByRoomSequenceDescCreatedAtDesc(NoticeChatRoom room, User senderUser, Long roomSequence);

    @Query("""
            SELECT new com.example.pogun.dto.noticechat.NoticeChatMessageProjection(
                m.id,
                r.id,
                sender.id,
                sender.nickname,
                m.message,
                m.messageType,
                m.isRead,
                m.createdAt,
                m.clientMessageId,
                m.roomSequence,
                m.editedAt,
                m.deletedAt,
                reply.id,
                replySender.id,
                replySender.nickname,
                reply.message,
                reply.messageType,
                reply.deletedAt
            )
            FROM NoticeChatMessage m
            JOIN m.room r
            JOIN m.senderUser sender
            LEFT JOIN m.replyToMessage reply
            LEFT JOIN reply.senderUser replySender
            WHERE r = :room
              AND m.deletedAt IS NULL
              AND (:beforeSequence IS NULL OR m.roomSequence < :beforeSequence)
            ORDER BY m.roomSequence DESC, m.createdAt DESC
            """)
    List<NoticeChatMessageProjection> findVisiblePageRows(
            @Param("room") NoticeChatRoom room,
            @Param("beforeSequence") Long beforeSequence,
            Pageable pageable
    );

    Optional<NoticeChatMessage> findByRoomAndSenderUserAndClientMessageId(NoticeChatRoom room, User senderUser, String clientMessageId);

    long countByRoomAndSenderUserNotAndDeletedAtIsNull(NoticeChatRoom room, User senderUser);

    long countByRoomAndSenderUserNotAndDeletedAtIsNullAndRoomSequenceGreaterThan(NoticeChatRoom room, User senderUser, Long roomSequence);

                @Query("""
                                                SELECT new com.example.pogun.dto.noticechat.NoticeChatRoomUnreadCountProjection(
                                                                state.room.id,
                                                                (
                                                                                SELECT COUNT(message)
                                                                                FROM NoticeChatMessage message
                                                                                WHERE message.room = state.room
                                                                                        AND message.deletedAt IS NULL
                                                                                        AND message.senderUser <> :currentUser
                                                                                        AND (
                                                                                                                state.lastReadRoomSequence IS NULL
                                                                                                                OR message.roomSequence > state.lastReadRoomSequence
                                                                                        )
                                                                )
                                                )
                                                FROM NoticeChatRoomParticipantState state
                                                WHERE state.user = :currentUser
                                                        AND state.leftAt IS NULL
                                                        AND state.room IN :rooms
                                                """)
                List<NoticeChatRoomUnreadCountProjection> findUnreadCountsByStateUserAndRooms(
                                                @Param("currentUser") User currentUser,
                                                @Param("rooms") List<NoticeChatRoom> rooms
                );

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE NoticeChatMessage m
            SET m.replyToMessage = NULL
            WHERE m.replyToMessage IN :messages
            """)
    int clearReplyTargets(@Param("messages") List<NoticeChatMessage> messages);

    void deleteByRoomIn(List<NoticeChatRoom> rooms);
}
