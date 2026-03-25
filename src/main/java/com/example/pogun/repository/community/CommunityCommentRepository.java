package com.example.pogun.repository.community;

import com.example.pogun.entity.community.CommunityComment;
import com.example.pogun.entity.community.CommunityPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
/**
 * 영속성 조회와 저장을 담당하는 CommunityCommentRepository이다.
 */

@Repository
public interface CommunityCommentRepository extends JpaRepository<CommunityComment, UUID> {
    List<CommunityComment> findByPostOrderByCreatedAtAsc(CommunityPost post);
}
