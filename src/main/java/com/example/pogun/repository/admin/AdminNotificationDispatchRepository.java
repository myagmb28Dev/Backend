package com.example.pogun.repository.admin;

import com.example.pogun.entity.admin.AdminNotificationDispatch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AdminNotificationDispatchRepository extends JpaRepository<AdminNotificationDispatch, UUID> {
    Page<AdminNotificationDispatch> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
