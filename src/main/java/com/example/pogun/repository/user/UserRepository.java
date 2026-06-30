package com.example.pogun.repository.user;

import com.example.pogun.entity.user.User;
import com.example.pogun.entity.user.enums.UserStatus;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.example.pogun.entity.user.enums.UserRole;
/**
 * 영속성 조회와 저장을 담당하는 UserRepository이다.
 */

@Repository
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {
    Optional<User> findByFirebaseUid(String firebaseUid);
    Optional<User> findByEmail(String email);
    boolean existsByFirebaseUid(String firebaseUid);
    long countByStatus(UserStatus status);
    long countByCreatedAtBetween(Instant from, Instant to);
    long countByRole(UserRole role);
    long countByRoleAndStatus(UserRole role, UserStatus status);
    List<User> findByRoleOrderByCreatedAtDesc(UserRole role);
    List<User> findByStatusAndWithdrawnAtLessThanEqual(UserStatus status, Instant withdrawnAt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update User u
            set u.lastActiveAt = :lastActiveAt
            where u.firebaseUid = :firebaseUid
            """)
        int updateLastActiveAtByFirebaseUid(
            @Param("firebaseUid") String firebaseUid,
            @Param("lastActiveAt") Instant lastActiveAt
    );
}
