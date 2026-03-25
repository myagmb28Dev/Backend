package com.example.pogun.repository.community;

import com.example.pogun.entity.community.CommunityPost;
import com.example.pogun.entity.community.CommunityPostReaction;
import com.example.pogun.entity.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
/**
 * 영속성 조회와 저장을 담당하는 CommunityPostReactionRepository이다.
 */

@Repository
public interface CommunityPostReactionRepository extends JpaRepository<CommunityPostReaction, UUID> {
    Optional<CommunityPostReaction> findByPostAndUser(CommunityPost post, User user);
    long countByPostAndReactionType(CommunityPost post, String reactionType);
    void deleteByPostIn(List<CommunityPost> posts);
}

