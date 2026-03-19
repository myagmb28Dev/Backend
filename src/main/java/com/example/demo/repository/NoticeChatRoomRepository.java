package com.example.demo.repository;

import com.example.demo.entity.NoticeChatRoom;
import com.example.demo.entity.PetNotice;
import com.example.demo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NoticeChatRoomRepository extends JpaRepository<NoticeChatRoom, UUID> {
    Optional<NoticeChatRoom> findByNoticeAndOwnerUserAndGuestUser(PetNotice notice, User ownerUser, User guestUser);

    List<NoticeChatRoom> findByOwnerUserIdOrGuestUserIdOrderByLastMessageAtDescCreatedAtDesc(UUID ownerUserId, UUID guestUserId);
}
