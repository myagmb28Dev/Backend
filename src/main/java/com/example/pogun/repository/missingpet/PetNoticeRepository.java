package com.example.pogun.repository.missingpet;

import com.example.pogun.entity.missingpet.PetNotice;
import com.example.pogun.entity.user.User;
import com.example.pogun.entity.missingpet.enums.PetNoticeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
/**
 * 영속성 조회와 저장을 담당하는 PetNoticeRepository이다.
 */

@Repository
public interface PetNoticeRepository extends JpaRepository<PetNotice, UUID> {
    List<PetNotice> findByAuthorOrderByCreatedAtDesc(User author);

    @Query("""
            SELECT n FROM PetNotice n
            WHERE n.hidden = false
              AND (:region IS NULL OR n.missingRegion = :region)
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

    long countByHiddenTrue();
}
