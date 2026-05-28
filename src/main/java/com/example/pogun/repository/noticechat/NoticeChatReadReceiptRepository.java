package com.example.pogun.repository.noticechat;

import com.example.pogun.entity.noticechat.NoticeChatReadReceipt;
import com.example.pogun.entity.noticechat.NoticeChatRoom;
import com.example.pogun.entity.user.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NoticeChatReadReceiptRepository extends JpaRepository<NoticeChatReadReceipt, UUID> {
    Optional<NoticeChatReadReceipt> findByRoomAndReader(NoticeChatRoom room, User reader);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select receipt from NoticeChatReadReceipt receipt where receipt.room = :room and receipt.reader = :reader")
    Optional<NoticeChatReadReceipt> findByRoomAndReaderForUpdate(@Param("room") NoticeChatRoom room, @Param("reader") User reader);

    void deleteByRoomIn(List<NoticeChatRoom> rooms);
}
