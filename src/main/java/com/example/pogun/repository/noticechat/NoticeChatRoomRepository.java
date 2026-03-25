package com.example.pogun.repository.noticechat;

import com.example.pogun.entity.noticechat.NoticeChatRoom;
import com.example.pogun.entity.missingpet.PetNotice;
import com.example.pogun.entity.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
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

    List<NoticeChatRoom> findByOwnerUserIdOrGuestUserIdOrderByLastMessageAtDescCreatedAtDesc(UUID ownerUserId, UUID guestUserId);

    List<NoticeChatRoom> findByNotice(PetNotice notice);
}
