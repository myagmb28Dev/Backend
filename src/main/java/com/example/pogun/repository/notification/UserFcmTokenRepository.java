package com.example.pogun.repository.notification;

import com.example.pogun.entity.user.User;
import com.example.pogun.entity.notification.UserFcmToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
/**
 * 영속성 조회와 저장을 담당하는 UserFcmTokenRepository이다.
 */

@Repository
public interface UserFcmTokenRepository extends JpaRepository<UserFcmToken, UUID> {
    Optional<UserFcmToken> findByToken(String token);

    List<UserFcmToken> findByUserAndActiveTrueOrderByUpdatedAtDesc(User user);
}
