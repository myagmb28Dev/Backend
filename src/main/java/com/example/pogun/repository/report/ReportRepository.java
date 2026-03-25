package com.example.pogun.repository.report;

import com.example.pogun.entity.report.Report;
import com.example.pogun.entity.user.User;
import com.example.pogun.entity.report.enums.ReportStatus;
import com.example.pogun.entity.report.enums.ReportTargetType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
/**
 * 영속성 조회와 저장을 담당하는 ReportRepository이다.
 */

@Repository
public interface ReportRepository extends JpaRepository<Report, UUID> {
    boolean existsByReporterAndTargetTypeAndTargetIdAndStatusIn(
            User reporter,
            ReportTargetType targetType,
            UUID targetId,
            Collection<ReportStatus> statuses
    );

    List<Report> findAllByOrderByCreatedAtDesc();

    long countByCreatedAtBetween(Instant from, Instant to);

    long countByStatusIn(Collection<ReportStatus> statuses);
}
