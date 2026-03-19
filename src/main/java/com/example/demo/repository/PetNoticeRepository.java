package com.example.demo.repository;

import com.example.demo.entity.PetNotice;
import com.example.demo.entity.User;
import com.example.demo.entity.enums.PetNoticeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface PetNoticeRepository extends JpaRepository<PetNotice, UUID> {
    List<PetNotice> findByAuthorOrderByCreatedAtDesc(User author);

    @Query("""
            SELECT n FROM PetNotice n
            WHERE (:region IS NULL OR n.missingRegion = :region)
              AND (:breed IS NULL OR n.breed = :breed)
              AND (:status IS NULL OR n.status = :status)
              AND (:from IS NULL OR n.missingDate >= :from)
              AND (:to IS NULL OR n.missingDate <= :to)
            """)
    List<PetNotice> findNotices(
            @Param("region") String region,
            @Param("breed") String breed,
            @Param("status") PetNoticeStatus status,
            @Param("from") Instant from,
            @Param("to") Instant to
    );
}
