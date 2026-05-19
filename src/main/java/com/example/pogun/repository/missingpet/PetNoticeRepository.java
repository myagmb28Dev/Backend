package com.example.pogun.repository.missingpet;

import com.example.pogun.entity.missingpet.PetNotice;
import com.example.pogun.entity.user.User;
import com.example.pogun.entity.missingpet.enums.PetNoticeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
              AND (:status IS NULL OR n.status = :status)
              AND n.missingDate >= COALESCE(:from, n.missingDate)
              AND n.missingDate <= COALESCE(:to, n.missingDate)
              AND (:region = '' OR lower(n.missingRegion) LIKE concat('%', :region, '%'))
              AND (:breed = '' OR (n.breed IS NOT NULL AND lower(n.breed) LIKE concat('%', :breed, '%')))
              AND (:query = ''
                   OR lower(n.title) LIKE concat('%', :query, '%')
                   OR (n.description IS NOT NULL AND lower(n.description) LIKE concat('%', :query, '%'))
                   OR lower(n.missingRegion) LIKE concat('%', :query, '%')
                   OR (n.breed IS NOT NULL AND lower(n.breed) LIKE concat('%', :query, '%')))
              AND (:authorId IS NULL OR n.author.id = :authorId)
            """)
    Page<PetNotice> searchNotices(
            @Param("status") PetNoticeStatus status,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("region") String region,
            @Param("breed") String breed,
            @Param("query") String query,
            @Param("authorId") UUID authorId,
            Pageable pageable
    );

    long countByHiddenTrue();
    long countByCreatedAtBetween(Instant from, Instant to);
    long countByStatusAndCreatedAtBetween(PetNoticeStatus status, Instant from, Instant to);
    long countByStatusAndUpdatedAtBetween(PetNoticeStatus status, Instant from, Instant to);
}
