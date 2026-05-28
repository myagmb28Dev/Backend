package com.example.pogun.repository.noticechat;

import com.example.pogun.entity.noticechat.NoticeChatMessage;
import com.example.pogun.entity.noticechat.NoticeChatRoom;
import com.example.pogun.entity.user.User;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
/**
 * 영속성 조회와 저장을 담당하는 NoticeChatMessageRepository이다.
 */

@Repository
public interface NoticeChatMessageRepository extends JpaRepository<NoticeChatMessage, UUID> {
    @EntityGraph(attributePaths = {"senderUser", "images", "replyToMessage", "replyToMessage.senderUser"})
    @Query("""
            SELECT m
            FROM NoticeChatMessage m
            WHERE m.room = :room
              AND m.deletedAt IS NULL
              AND (:beforeSequence IS NULL OR m.roomSequence < :beforeSequence)
            ORDER BY m.roomSequence DESC, m.createdAt DESC
            """)
    List<NoticeChatMessage> findVisiblePage(
            @Param("room") NoticeChatRoom room,
            @Param("beforeSequence") Long beforeSequence,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"senderUser", "images", "replyToMessage", "replyToMessage.senderUser"})
    @Query("""
            SELECT m
            FROM NoticeChatMessage m
            WHERE m.room = :room
              AND m.deletedAt IS NULL
              AND LOWER(m.message) LIKE LOWER(CONCAT('%', :keyword, '%'))
            ORDER BY m.roomSequence ASC, m.createdAt ASC
            """)
    List<NoticeChatMessage> searchVisibleText(@Param("room") NoticeChatRoom room, @Param("keyword") String keyword);

    Optional<NoticeChatMessage> findTopByRoomAndDeletedAtIsNullOrderByRoomSequenceDescCreatedAtDesc(NoticeChatRoom room);

    Optional<NoticeChatMessage> findTopByRoomAndSenderUserNotAndDeletedAtIsNullOrderByRoomSequenceDescCreatedAtDesc(
            NoticeChatRoom room,
            User senderUser
    );

    Optional<NoticeChatMessage> findByRoomAndSenderUserAndClientMessageId(NoticeChatRoom room, User senderUser, String clientMessageId);

    @Query("""
            SELECT COUNT(m)
            FROM NoticeChatMessage m
            WHERE m.room = :room
              AND m.senderUser <> :reader
              AND m.deletedAt IS NULL
              AND COALESCE(m.roomSequence, 0) > :lastReadRoomSequence
            """)
    long countUnreadByWatermark(
            @Param("room") NoticeChatRoom room,
            @Param("reader") User reader,
            @Param("lastReadRoomSequence") long lastReadRoomSequence
    );

    List<NoticeChatMessage> findByDeletedAtBefore(Instant cutoff);

    @Modifying
    @Query("""
            UPDATE NoticeChatMessage message
            SET message.replyToMessage = NULL
            WHERE message.replyToMessage IN :messages
            """)
    void clearReplyTargets(@Param("messages") List<NoticeChatMessage> messages);

    List<NoticeChatMessage> findByRoomIn(List<NoticeChatRoom> rooms);

    void deleteByRoomIn(List<NoticeChatRoom> rooms);
}
