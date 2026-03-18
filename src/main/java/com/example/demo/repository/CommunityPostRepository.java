package com.example.demo.repository;

import com.example.demo.entity.CommunityPost;
import com.example.demo.entity.enums.CommunityPostStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CommunityPostRepository extends JpaRepository<CommunityPost, UUID> {

    @Query("SELECT p FROM CommunityPost p WHERE (:status IS NULL OR p.status = :status) " +
           "AND (:q IS NULL OR p.title LIKE %:q% OR p.content LIKE %:q%)")
    Page<CommunityPost> findPosts(@Param("status") CommunityPostStatus status, 
                                 @Param("q") String q, 
                                 Pageable pageable);
}
