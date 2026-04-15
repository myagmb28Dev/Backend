package com.example.pogun.repository.noticechat;

import com.example.pogun.entity.noticechat.NoticeChatRoom;
import com.example.pogun.entity.noticechat.NoticeChatRoomParticipantState;
import com.example.pogun.entity.user.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NoticeChatRoomParticipantStateRepository extends JpaRepository<NoticeChatRoomParticipantState, UUID> {
    Optional<NoticeChatRoomParticipantState> findByRoomAndUser(NoticeChatRoom room, User user);

    @EntityGraph(attributePaths = {"room", "room.notice", "room.ownerUser", "room.guestUser", "lastReadMessage"})
    List<NoticeChatRoomParticipantState> findByUserAndLeftAtIsNull(User user);

        @EntityGraph(attributePaths = {"room", "room.notice", "room.ownerUser", "room.guestUser", "lastReadMessage"})
        @Query("""
            SELECT state
            FROM NoticeChatRoomParticipantState state
            JOIN state.room room
            JOIN room.notice notice
            JOIN room.ownerUser owner
            JOIN room.guestUser guest
            WHERE state.user = :user
              AND state.leftAt IS NULL
              AND (
                LOWER(notice.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR (owner.id = :userId AND LOWER(guest.nickname) LIKE LOWER(CONCAT('%', :keyword, '%')))
                OR (guest.id = :userId AND LOWER(owner.nickname) LIKE LOWER(CONCAT('%', :keyword, '%')))
              )
            """)
        List<NoticeChatRoomParticipantState> findByUserAndLeftAtIsNullAndKeyword(
            @Param("user") User user,
            @Param("userId") UUID userId,
            @Param("keyword") String keyword
        );

        @EntityGraph(attributePaths = {"room", "user", "lastReadMessage"})
        List<NoticeChatRoomParticipantState> findByRoomIn(List<NoticeChatRoom> rooms);

    List<NoticeChatRoomParticipantState> findByUser(User user);

    List<NoticeChatRoomParticipantState> findByRoom(NoticeChatRoom room);
}
