package com.example.demo.repository;

import com.example.demo.entity.NoticeChatMessage;
import com.example.demo.entity.NoticeChatRoom;
import com.example.demo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NoticeChatMessageRepository extends JpaRepository<NoticeChatMessage, UUID> {
    List<NoticeChatMessage> findByRoomOrderByCreatedAtAsc(NoticeChatRoom room);

    long countByRoomAndSenderUserNotAndIsReadFalse(NoticeChatRoom room, User senderUser);
}
