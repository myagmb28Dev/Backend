package com.example.pogun.repository.noticechat;

import com.example.pogun.entity.noticechat.NoticeChatRoom;
import com.example.pogun.entity.noticechat.NoticeChatRoomParticipantState;
import com.example.pogun.entity.user.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NoticeChatRoomParticipantStateRepository extends JpaRepository<NoticeChatRoomParticipantState, UUID> {
    Optional<NoticeChatRoomParticipantState> findByRoomAndUser(NoticeChatRoom room, User user);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select state from NoticeChatRoomParticipantState state where state.room = :room and state.user = :user")
    Optional<NoticeChatRoomParticipantState> findByRoomAndUserForUpdate(@Param("room") NoticeChatRoom room, @Param("user") User user);

    List<NoticeChatRoomParticipantState> findByUser(User user);

    void deleteByRoomIn(List<NoticeChatRoom> rooms);

    @Query("""
            SELECT state
            FROM NoticeChatRoomParticipantState state
            JOIN FETCH state.room room
            JOIN FETCH room.ownerUser
            JOIN FETCH room.guestUser
            WHERE state.customThumbnailUrl = :url
            """)
    Optional<NoticeChatRoomParticipantState> findByCustomThumbnailUrl(@Param("url") String url);
}
