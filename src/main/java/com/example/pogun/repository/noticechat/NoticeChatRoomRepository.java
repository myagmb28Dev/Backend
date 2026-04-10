package com.example.pogun.repository.noticechat;

import com.example.pogun.entity.missingpet.PetNotice;
import com.example.pogun.entity.noticechat.NoticeChatRoom;
import com.example.pogun.entity.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
/**
 * 영속성 조회와 저장을 담당하는 NoticeChatRoomRepository이다.
 */

@Repository
public interface NoticeChatRoomRepository extends JpaRepository<NoticeChatRoom, UUID> {
    Optional<NoticeChatRoom> findByNoticeAndOwnerUserAndGuestUser(PetNotice notice, User ownerUser, User guestUser);

    @Query("""
            SELECT r
            FROM NoticeChatRoom r
            WHERE r.notice IS NOT NULL
              AND (r.ownerUser.id = :userId OR r.guestUser.id = :userId)
            ORDER BY COALESCE(r.lastMessageAt, r.createdAt) DESC, r.createdAt DESC
            """)
    List<NoticeChatRoom> findVisibleRoomsForUser(@org.springframework.data.repository.query.Param("userId") UUID userId);

    List<NoticeChatRoom> findByNotice(PetNotice notice);
}
