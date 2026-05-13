package com.example.pogun.repository.notification;

import com.example.pogun.entity.user.User;
import com.example.pogun.entity.notification.UserFcmToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
/**
 * 영속성 조회와 저장을 담당하는 UserFcmTokenRepository이다.
 */

@Repository
public interface UserFcmTokenRepository extends JpaRepository<UserFcmToken, UUID> {
    Optional<UserFcmToken> findByToken(String token);
    Optional<UserFcmToken> findFirstByUserAndPlatformAndDeviceIdOrderByUpdatedAtDesc(User user, String platform, String deviceId);

    List<UserFcmToken> findByUserAndActiveTrueOrderByUpdatedAtDesc(User user);
    List<UserFcmToken> findByUserOrderByUpdatedAtDesc(User user);
    Optional<UserFcmToken> findByIdAndUser(UUID id, User user);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update UserFcmToken t
               set t.active = false
             where t.user = :user
               and t.active = true
               and t.platform = :platform
               and t.deviceId = :deviceId
               and t.token <> :currentToken
            """)
    int deactivateActiveTokensForSameDeviceExcludingCurrent(
            @Param("user") User user,
            @Param("platform") String platform,
            @Param("deviceId") String deviceId,
            @Param("currentToken") String currentToken
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from UserFcmToken t
             where t.active = false
               and t.updatedAt < :cutoff
            """)
    int deleteInactiveTokensOlderThan(@Param("cutoff") Instant cutoff);
}

