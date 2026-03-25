package com.example.pogun.service.community;

import com.example.pogun.entity.community.CommunityComment;
import com.example.pogun.entity.community.enums.CommunityCommentStatus;
import com.example.pogun.entity.community.CommunityPost;
import com.example.pogun.entity.community.enums.CommunityPostStatus;
import com.example.pogun.repository.community.CommunityCommentRepository;
import com.example.pogun.repository.community.CommunityPostReactionRepository;
import com.example.pogun.repository.community.CommunityPostRepository;
import com.example.pogun.repository.community.CommunityPostVoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 소프트 삭제된 커뮤니티 데이터를 일정 기간 후 물리 삭제하는 정리 작업이다.
 */
@Service
@RequiredArgsConstructor
public class CommunityPurgeService {

    private static final Duration RETENTION_PERIOD = Duration.ofDays(30);

    private final CommunityPostRepository communityPostRepository;
    private final CommunityCommentRepository communityCommentRepository;
    private final CommunityPostVoteRepository communityPostVoteRepository;
    private final CommunityPostReactionRepository communityPostReactionRepository;

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void purgeDeletedCommunityContent() {
        Instant cutoff = Instant.now().minus(RETENTION_PERIOD);
        purgeDeletedPosts(cutoff);
        purgeDeletedComments(cutoff);
    }

    private void purgeDeletedPosts(Instant cutoff) {
        List<CommunityPost> expiredPosts = communityPostRepository.findByStatusAndUpdatedAtBefore(CommunityPostStatus.DELETED, cutoff);
        if (expiredPosts.isEmpty()) {
            return;
        }

        communityCommentRepository.deleteByPostIn(expiredPosts);
        communityPostVoteRepository.deleteByPostIn(expiredPosts);
        communityPostReactionRepository.deleteByPostIn(expiredPosts);
        communityPostRepository.deleteAll(expiredPosts);
    }

    private void purgeDeletedComments(Instant cutoff) {
        List<CommunityComment> expiredComments = communityCommentRepository.findByStatusAndUpdatedAtBefore(CommunityCommentStatus.DELETED, cutoff);
        if (expiredComments.isEmpty()) {
            return;
        }

        communityCommentRepository.deleteAll(expiredComments);
    }
}

