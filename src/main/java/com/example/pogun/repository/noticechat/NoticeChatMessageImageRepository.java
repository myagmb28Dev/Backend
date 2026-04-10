package com.example.pogun.repository.noticechat;

import com.example.pogun.entity.noticechat.NoticeChatMessageImage;
import com.example.pogun.entity.noticechat.NoticeChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * 채팅 메시지 이미지 영속성 저장소이다.
 */
@Repository
public interface NoticeChatMessageImageRepository extends JpaRepository<NoticeChatMessageImage, UUID> {

    void deleteByMessageRoomIn(List<NoticeChatRoom> rooms);
}
