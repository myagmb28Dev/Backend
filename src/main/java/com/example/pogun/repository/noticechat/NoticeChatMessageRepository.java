package com.example.pogun.repository.noticechat;

import com.example.pogun.entity.noticechat.NoticeChatMessage;
import com.example.pogun.entity.noticechat.NoticeChatRoom;
import com.example.pogun.entity.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
/**
 * 영속성 조회와 저장을 담당하는 NoticeChatMessageRepository이다.
 */

@Repository
public interface NoticeChatMessageRepository extends JpaRepository<NoticeChatMessage, UUID> {
    List<NoticeChatMessage> findByRoomOrderByCreatedAtAsc(NoticeChatRoom room);

    long countByRoomAndSenderUserNotAndIsReadFalse(NoticeChatRoom room, User senderUser);

    void deleteByRoomIn(List<NoticeChatRoom> rooms);
}
