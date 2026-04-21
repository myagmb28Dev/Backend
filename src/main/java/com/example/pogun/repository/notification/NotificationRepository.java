package com.example.pogun.repository.notification;

import com.example.pogun.entity.notification.Notification;
import com.example.pogun.entity.notification.enums.NotificationType;
import com.example.pogun.entity.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
/**
 * 영속성 조회와 저장을 담당하는 NotificationRepository이다.
 */

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    List<Notification> findByUserOrderByCreatedAtDesc(User user);

    Page<Notification> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

    Page<Notification> findByUserAndIsReadFalseOrderByCreatedAtDesc(User user, Pageable pageable);

    Page<Notification> findByUserAndTypeOrderByCreatedAtDesc(User user, NotificationType type, Pageable pageable);

    Page<Notification> findByUserAndTypeAndIsReadFalseOrderByCreatedAtDesc(User user, NotificationType type, Pageable pageable);

    long countByUserAndIsReadFalse(User user);

    Optional<Notification> findByDedupKey(String dedupKey);

    @Query(value = """
            select *
            from notifications
            where user_id = :userId
              and is_read = false
              and target_type = :targetType
              and type in (:types)
              and metadata ->> 'roomId' = cast(:roomId as text)
            order by created_at desc
            """, nativeQuery = true)
    List<Notification> findUnreadByUserIdAndRoomIdAndTargetTypeAndTypeIn(
            @Param("userId") UUID userId,
            @Param("roomId") UUID roomId,
            @Param("targetType") String targetType,
            @Param("types") List<String> types
    );
}

