package com.example.pogun.repository.admin;

import com.example.pogun.entity.admin.AdminSession;
import com.example.pogun.entity.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AdminSessionRepository extends JpaRepository<AdminSession, UUID> {
    Optional<AdminSession> findByTokenHash(String tokenHash);
    List<AdminSession> findByUserAndRevokedAtIsNullAndExpiresAtAfter(User user, Instant now);
}
