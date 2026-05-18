package com.example.pogun.repository.user;

import com.example.pogun.entity.user.PendingSocialSignup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PendingSocialSignupRepository extends JpaRepository<PendingSocialSignup, UUID> {
    Optional<PendingSocialSignup> findByFirebaseUid(String firebaseUid);

    void deleteByFirebaseUid(String firebaseUid);
    long deleteByEmailIgnoreCase(String email);

    long deleteByExpiresAtBefore(Instant now);
}
