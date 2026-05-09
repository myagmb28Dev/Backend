package com.example.pogun.repository.admin;

import com.example.pogun.entity.admin.AdminAuthChallenge;
import com.example.pogun.entity.admin.AdminSession;
import com.example.pogun.entity.admin.enums.AdminAuthChallengeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AdminAuthChallengeRepository extends JpaRepository<AdminAuthChallenge, UUID> {
    Optional<AdminAuthChallenge> findByIdAndSessionAndType(UUID id, AdminSession session, AdminAuthChallengeType type);
    List<AdminAuthChallenge> findBySessionAndTypeAndUsedAtIsNull(AdminSession session, AdminAuthChallengeType type);
}
