package com.example.pogun.repository.noticechat;

import com.example.pogun.entity.noticechat.NoticeChatMessageImage;
import com.example.pogun.entity.noticechat.NoticeChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 채팅 메시지 이미지 영속성 저장소이다.
 */
@Repository
public interface NoticeChatMessageImageRepository extends JpaRepository<NoticeChatMessageImage, UUID> {

    void deleteByMessageRoomIn(List<NoticeChatRoom> rooms);

    @Query("""
            SELECT image
            FROM NoticeChatMessageImage image
            JOIN FETCH image.message message
            JOIN FETCH message.room room
            JOIN FETCH room.ownerUser
            JOIN FETCH room.guestUser
            WHERE image.imageUrl = :url
               OR image.originalUrl = :url
               OR image.webpUrl = :url
               OR image.mediumUrl = :url
               OR image.thumbnailUrl = :url
               OR image.previewUrl = :url
            """)
    Optional<NoticeChatMessageImage> findByAnyUrl(@Param("url") String url);
}
