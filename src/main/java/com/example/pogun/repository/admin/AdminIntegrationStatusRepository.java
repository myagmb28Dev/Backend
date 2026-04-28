package com.example.pogun.repository.admin;

import com.example.pogun.entity.admin.AdminIntegrationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AdminIntegrationStatusRepository extends JpaRepository<AdminIntegrationStatus, UUID> {
    List<AdminIntegrationStatus> findTop20ByIntegrationKeyOrderByCreatedAtDesc(String integrationKey);
    Optional<AdminIntegrationStatus> findTopByIntegrationKeyOrderByCreatedAtDesc(String integrationKey);
    List<AdminIntegrationStatus> findTop50ByOrderByCreatedAtDesc();
}
