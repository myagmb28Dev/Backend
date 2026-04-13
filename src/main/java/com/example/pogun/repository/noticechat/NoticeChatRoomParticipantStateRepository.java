package com.example.pogun.repository.noticechat;

import com.example.pogun.entity.noticechat.NoticeChatRoom;
import com.example.pogun.entity.noticechat.NoticeChatRoomParticipantState;
import com.example.pogun.entity.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NoticeChatRoomParticipantStateRepository extends JpaRepository<NoticeChatRoomParticipantState, UUID> {
    Optional<NoticeChatRoomParticipantState> findByRoomAndUser(NoticeChatRoom room, User user);

    List<NoticeChatRoomParticipantState> findByUserAndLeftAtIsNull(User user);

    List<NoticeChatRoomParticipantState> findByUser(User user);

    List<NoticeChatRoomParticipantState> findByRoom(NoticeChatRoom room);
}
