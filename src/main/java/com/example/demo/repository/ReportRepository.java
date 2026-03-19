package com.example.demo.repository;

import com.example.demo.entity.Report;
import com.example.demo.entity.User;
import com.example.demo.entity.enums.ReportStatus;
import com.example.demo.entity.enums.ReportTargetType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface ReportRepository extends JpaRepository<Report, UUID> {
    boolean existsByReporterAndTargetTypeAndTargetIdAndStatusIn(
            User reporter,
            ReportTargetType targetType,
            UUID targetId,
            Collection<ReportStatus> statuses
    );

    List<Report> findAllByOrderByCreatedAtDesc();
}
