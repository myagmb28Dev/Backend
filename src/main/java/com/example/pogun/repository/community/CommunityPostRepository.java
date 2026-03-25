package com.example.pogun.repository.community;

import com.example.pogun.entity.community.CommunityPost;
import com.example.pogun.entity.community.CommunityPostStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
/**
 * 영속성 조회와 저장을 담당하는 CommunityPostRepository이다.
 */

@Repository
public interface CommunityPostRepository extends JpaRepository<CommunityPost, UUID> {

    @Query(value = """
            SELECT DISTINCT p
            FROM CommunityPost p
            LEFT JOIN p.tags tag
            WHERE (:status IS NULL OR p.status = :status)
              AND (:category IS NULL OR LOWER(p.category) = LOWER(:category))
              AND (:tag IS NULL OR LOWER(tag) = LOWER(:tag))
              AND (
                    :q IS NULL
                    OR LOWER(p.title) LIKE LOWER(CONCAT('%', :q, '%'))
                    OR LOWER(p.content) LIKE LOWER(CONCAT('%', :q, '%'))
                    OR LOWER(p.category) LIKE LOWER(CONCAT('%', :q, '%'))
                    OR LOWER(tag) LIKE LOWER(CONCAT('%', :q, '%'))
                    OR LOWER(p.author.nickname) LIKE LOWER(CONCAT('%', :q, '%'))
              )
            """,
            countQuery = """
            SELECT COUNT(DISTINCT p)
            FROM CommunityPost p
            LEFT JOIN p.tags tag
            WHERE (:status IS NULL OR p.status = :status)
              AND (:category IS NULL OR LOWER(p.category) = LOWER(:category))
              AND (:tag IS NULL OR LOWER(tag) = LOWER(:tag))
              AND (
                    :q IS NULL
                    OR LOWER(p.title) LIKE LOWER(CONCAT('%', :q, '%'))
                    OR LOWER(p.content) LIKE LOWER(CONCAT('%', :q, '%'))
                    OR LOWER(p.category) LIKE LOWER(CONCAT('%', :q, '%'))
                    OR LOWER(tag) LIKE LOWER(CONCAT('%', :q, '%'))
                    OR LOWER(p.author.nickname) LIKE LOWER(CONCAT('%', :q, '%'))
              )
            """)
    Page<CommunityPost> findPosts(@Param("status") CommunityPostStatus status,
                                 @Param("category") String category,
                                 @Param("tag") String tag,
                                 @Param("q") String q,
                                 Pageable pageable);

    List<CommunityPost> findByAuthorIdOrderByCreatedAtDesc(UUID authorId);

    long countByStatus(CommunityPostStatus status);
}
