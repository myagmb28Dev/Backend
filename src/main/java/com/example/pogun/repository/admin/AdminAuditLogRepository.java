package com.example.pogun.repository.admin;

import com.example.pogun.entity.admin.AdminAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, UUID> {
    Page<AdminAuditLog> findByActionContainingIgnoreCaseOrTargetTypeContainingIgnoreCase(String action, String targetType, Pageable pageable);
}
