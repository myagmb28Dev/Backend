package com.example.pogun.repository.noticechat;

import com.example.pogun.entity.noticechat.NoticeChatMessage;
import com.example.pogun.entity.noticechat.NoticeChatRoom;
import com.example.pogun.entity.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
/**
 * 영속성 조회와 저장을 담당하는 NoticeChatMessageRepository이다.
 */

@Repository
public interface NoticeChatMessageRepository extends JpaRepository<NoticeChatMessage, UUID> {
    @Query("""
            SELECT m
            FROM NoticeChatMessage m
            LEFT JOIN FETCH m.senderUser
            LEFT JOIN FETCH m.replyToMessage reply
            LEFT JOIN FETCH reply.senderUser
            WHERE m.room = :room
              AND m.deletedAt IS NULL
            ORDER BY m.roomSequence ASC, m.createdAt ASC
            """)
    List<NoticeChatMessage> findVisibleByRoom(@Param("room") NoticeChatRoom room);

    @Query("""
            SELECT DISTINCT m
            FROM NoticeChatMessage m
            LEFT JOIN FETCH m.senderUser
            LEFT JOIN FETCH m.images
            LEFT JOIN FETCH m.replyToMessage reply
            LEFT JOIN FETCH reply.senderUser
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

    @Query("""
            SELECT m
            FROM NoticeChatMessage m
            WHERE m.room = :room
              AND m.deletedAt IS NULL
              AND LOWER(m.message) LIKE LOWER(CONCAT('%', :keyword, '%'))
            ORDER BY m.roomSequence ASC, m.createdAt ASC
            """)
    List<NoticeChatMessage> searchVisibleText(@Param("room") NoticeChatRoom room, @Param("keyword") String keyword);

    long countByRoomAndSenderUserNotAndIsReadFalse(NoticeChatRoom room, User senderUser);

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
            @Param("lastReadRoomSequence") Long lastReadRoomSequence
    );

    Optional<NoticeChatMessage> findByRoomAndSenderUserAndClientMessageId(NoticeChatRoom room, User senderUser, String clientMessageId);

    Optional<NoticeChatMessage> findTopByRoomAndDeletedAtIsNullOrderByRoomSequenceDescCreatedAtDesc(NoticeChatRoom room);

    void deleteByRoomIn(List<NoticeChatRoom> rooms);
}
