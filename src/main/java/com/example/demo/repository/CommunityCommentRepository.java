package com.example.demo.repository;

import com.example.demo.entity.CommunityComment;
import com.example.demo.entity.CommunityPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CommunityCommentRepository extends JpaRepository<CommunityComment, UUID> {
    List<CommunityComment> findByPostOrderByCreatedAtAsc(CommunityPost post);
}
