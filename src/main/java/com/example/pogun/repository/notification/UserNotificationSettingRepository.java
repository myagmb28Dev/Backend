package com.example.pogun.repository.notification;

import com.example.pogun.entity.notification.UserNotificationSetting;
import com.example.pogun.entity.notification.enums.NotificationType;
import com.example.pogun.entity.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserNotificationSettingRepository extends JpaRepository<UserNotificationSetting, UUID> {
    Optional<UserNotificationSetting> findByUserAndType(User user, NotificationType type);

    List<UserNotificationSetting> findByUser(User user);
}
