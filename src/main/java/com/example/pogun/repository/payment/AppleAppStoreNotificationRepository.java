package com.example.pogun.repository.payment;

import com.example.pogun.entity.payment.AppleAppStoreNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AppleAppStoreNotificationRepository extends JpaRepository<AppleAppStoreNotification, UUID> {
    Optional<AppleAppStoreNotification> findByNotificationUuid(String notificationUuid);
}
