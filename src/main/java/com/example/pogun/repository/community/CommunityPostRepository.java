package com.example.pogun.repository.community;

import com.example.pogun.entity.community.CommunityPost;
import com.example.pogun.entity.community.enums.CommunityPostStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
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
            WHERE p.status = :status
              AND (:category = '' OR LOWER(p.category) = :category)
              AND (:tag = '' OR LOWER(tag) = :tag)
              AND (
                    :q = ''
                    OR LOWER(p.title) LIKE CONCAT('%', :q, '%')
                    OR LOWER(p.content) LIKE CONCAT('%', :q, '%')
                    OR LOWER(p.category) LIKE CONCAT('%', :q, '%')
                    OR LOWER(tag) LIKE CONCAT('%', :q, '%')
                    OR LOWER(p.author.nickname) LIKE CONCAT('%', :q, '%')
              )
            """,
            countQuery = """
            SELECT COUNT(DISTINCT p)
            FROM CommunityPost p
            LEFT JOIN p.tags tag
            WHERE p.status = :status
              AND (:category = '' OR LOWER(p.category) = :category)
              AND (:tag = '' OR LOWER(tag) = :tag)
              AND (
                    :q = ''
                    OR LOWER(p.title) LIKE CONCAT('%', :q, '%')
                    OR LOWER(p.content) LIKE CONCAT('%', :q, '%')
                    OR LOWER(p.category) LIKE CONCAT('%', :q, '%')
                    OR LOWER(tag) LIKE CONCAT('%', :q, '%')
                    OR LOWER(p.author.nickname) LIKE CONCAT('%', :q, '%')
              )
            """)
    Page<CommunityPost> findPosts(@Param("status") CommunityPostStatus status,
                                 @Param("category") String category,
                                 @Param("tag") String tag,
                                 @Param("q") String q,
                                 Pageable pageable);

    @Query(value = """
            SELECT p
            FROM CommunityPost p
            WHERE p.status = :status
              AND (:category = '' OR LOWER(p.category) = :category)
              AND (
                    LOWER(p.title) LIKE CONCAT('%', :query, '%')
                    OR LOWER(p.content) LIKE CONCAT('%', :query, '%')
              )
            """,
            countQuery = """
            SELECT COUNT(p)
            FROM CommunityPost p
            WHERE p.status = :status
              AND (:category = '' OR LOWER(p.category) = :category)
              AND (
                    LOWER(p.title) LIKE CONCAT('%', :query, '%')
                    OR LOWER(p.content) LIKE CONCAT('%', :query, '%')
              )
            """)
    Page<CommunityPost> searchPosts(@Param("status") CommunityPostStatus status,
                                    @Param("category") String category,
                                    @Param("query") String query,
                                    Pageable pageable);

    List<CommunityPost> findByAuthorIdAndStatusNotOrderByCreatedAtDesc(UUID authorId, CommunityPostStatus status);

    List<CommunityPost> findByStatusAndUpdatedAtBefore(CommunityPostStatus status, Instant updatedAt);

    long countByStatus(CommunityPostStatus status);
    long countByCreatedAtBetween(Instant from, Instant to);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE CommunityPost p
            SET p.likeCount = CASE
                    WHEN COALESCE(p.likeCount, 0) + :delta < 0 THEN 0
                    ELSE COALESCE(p.likeCount, 0) + :delta
                END
            WHERE p.id = :postId
            """)
    int adjustLikeCount(@Param("postId") UUID postId, @Param("delta") long delta);
}

