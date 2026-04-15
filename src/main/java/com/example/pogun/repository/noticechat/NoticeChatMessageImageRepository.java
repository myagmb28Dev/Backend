package com.example.pogun.repository.noticechat;

import com.example.pogun.dto.noticechat.NoticeChatMessageImageProjection;
import com.example.pogun.entity.noticechat.NoticeChatMessageImage;
import com.example.pogun.entity.noticechat.NoticeChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * 채팅 메시지 이미지 영속성 저장소이다.
 */
@Repository
public interface NoticeChatMessageImageRepository extends JpaRepository<NoticeChatMessageImage, UUID> {

    @Query("""
            SELECT new com.example.pogun.dto.noticechat.NoticeChatMessageImageProjection(
                message.id,
                image.id,
                image.imageUrl,
                image.webpUrl,
                image.mediumUrl,
                image.thumbnailUrl,
                image.previewUrl,
                image.displayOrder
            )
            FROM NoticeChatMessageImage image
            JOIN image.message message
            WHERE message.id IN :messageIds
            ORDER BY message.roomSequence ASC, image.displayOrder ASC
            """)
    List<NoticeChatMessageImageProjection> findProjectedByMessageIds(@Param("messageIds") List<UUID> messageIds);

    void deleteByMessageRoomIn(List<NoticeChatRoom> rooms);
}
