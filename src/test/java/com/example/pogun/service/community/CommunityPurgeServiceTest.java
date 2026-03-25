package com.example.pogun.service.community;

import com.example.pogun.entity.community.CommunityComment;
import com.example.pogun.entity.community.enums.CommunityCommentStatus;
import com.example.pogun.entity.community.CommunityPost;
import com.example.pogun.entity.community.enums.CommunityPostStatus;
import com.example.pogun.repository.community.CommunityCommentRepository;
import com.example.pogun.repository.community.CommunityPostReactionRepository;
import com.example.pogun.repository.community.CommunityPostRepository;
import com.example.pogun.repository.community.CommunityPostVoteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CommunityPurgeServiceTest {

    @Mock
    private CommunityPostRepository communityPostRepository;

    @Mock
    private CommunityCommentRepository communityCommentRepository;

    @Mock
    private CommunityPostVoteRepository communityPostVoteRepository;

    @Mock
    private CommunityPostReactionRepository communityPostReactionRepository;

    @InjectMocks
    private CommunityPurgeService communityPurgeService;

    @Test
    void purgeDeletedCommunityContent_removesExpiredPostsAndComments() {
        CommunityPost post = CommunityPost.builder()
                .status(CommunityPostStatus.DELETED)
                .build();
        CommunityComment comment = CommunityComment.builder()
                .status(CommunityCommentStatus.DELETED)
                .post(post)
                .build();

        given(communityPostRepository.findByStatusAndUpdatedAtBefore(eq(CommunityPostStatus.DELETED), any(Instant.class)))
                .willReturn(List.of(post));
        given(communityCommentRepository.findByStatusAndUpdatedAtBefore(eq(CommunityCommentStatus.DELETED), any(Instant.class)))
                .willReturn(List.of(comment));

        communityPurgeService.purgeDeletedCommunityContent();

        verify(communityCommentRepository).deleteByPostIn(List.of(post));
        verify(communityPostVoteRepository).deleteByPostIn(List.of(post));
        verify(communityPostReactionRepository).deleteByPostIn(List.of(post));
        verify(communityPostRepository).deleteAll(List.of(post));
        verify(communityCommentRepository).deleteAll(List.of(comment));
    }
}

