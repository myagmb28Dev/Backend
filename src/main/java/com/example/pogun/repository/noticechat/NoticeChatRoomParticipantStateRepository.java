package com.example.pogun.repository.noticechat;

import com.example.pogun.entity.noticechat.NoticeChatRoom;
import com.example.pogun.entity.noticechat.NoticeChatRoomParticipantState;
import com.example.pogun.entity.user.User;
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

    List<NoticeChatRoomParticipantState> findByUser(User user);

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
