package com.example.pogun.repository.noticechat;

import com.example.pogun.entity.noticechat.NoticeChatMessage;
import com.example.pogun.entity.noticechat.NoticeChatRoom;
import com.example.pogun.entity.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
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
    List<NoticeChatMessage> findByRoomOrderByRoomSequenceAscCreatedAtAsc(NoticeChatRoom room);

    List<NoticeChatMessage> findByRoomAndMessageContainingIgnoreCaseOrderByRoomSequenceAscCreatedAtAsc(NoticeChatRoom room, String message);

    Optional<NoticeChatMessage> findTopByRoomOrderByCreatedAtDesc(NoticeChatRoom room);

    Optional<NoticeChatMessage> findByRoomAndSenderUserAndClientMessageId(NoticeChatRoom room, User senderUser, String clientMessageId);

    @Query("SELECT COALESCE(MAX(m.roomSequence), 0) FROM NoticeChatMessage m WHERE m.room = :room")
    long findMaxRoomSequence(@Param("room") NoticeChatRoom room);

    long countByRoomAndSenderUserNotAndIsReadFalse(NoticeChatRoom room, User senderUser);

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE NoticeChatMessage m
            SET m.isRead = true
            WHERE m.room = :room
              AND m.senderUser <> :reader
              AND m.isRead = false
            """)
    int markUnreadMessagesAsRead(@Param("room") NoticeChatRoom room, @Param("reader") User reader);

    void deleteByRoomIn(List<NoticeChatRoom> rooms);
}
