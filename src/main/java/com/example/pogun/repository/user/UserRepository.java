package com.example.pogun.repository.user;

import com.example.pogun.entity.user.User;
import com.example.pogun.entity.user.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
/**
 * 영속성 조회와 저장을 담당하는 UserRepository이다.
 */

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByFirebaseUid(String firebaseUid);
    Optional<User> findByEmail(String email);
    long countByStatus(UserStatus status);
}