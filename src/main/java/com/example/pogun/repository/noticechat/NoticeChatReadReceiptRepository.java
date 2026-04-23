package com.example.pogun.repository.noticechat;

import com.example.pogun.entity.noticechat.NoticeChatReadReceipt;
import com.example.pogun.entity.noticechat.NoticeChatRoom;
import com.example.pogun.entity.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NoticeChatReadReceiptRepository extends JpaRepository<NoticeChatReadReceipt, UUID> {
    Optional<NoticeChatReadReceipt> findByRoomAndReader(NoticeChatRoom room, User reader);

    void deleteByRoomIn(List<NoticeChatRoom> rooms);
}
