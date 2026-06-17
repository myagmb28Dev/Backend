package com.example.pogun.service.missingpet;

import com.example.pogun.dto.ai.MissingPetAnalysisRequestResponse;
import com.example.pogun.dto.common.ApiResponse.ApiException;
import com.example.pogun.entity.missingpet.PetNotice;
import com.example.pogun.entity.missingpet.enums.PetGender;
import com.example.pogun.entity.missingpet.enums.PetNoticeStatus;
import com.example.pogun.entity.user.User;
import com.example.pogun.entity.user.enums.UserRole;
import com.example.pogun.entity.user.enums.UserStatus;
import com.example.pogun.repository.bookmark.NoticeBookmarkRepository;
import com.example.pogun.repository.missingpet.PetNoticeRepository;
import com.example.pogun.repository.user.UserRepository;
import com.example.pogun.service.ai.AiService;
import com.example.pogun.service.cache.AiSourceCacheService;
import com.example.pogun.service.noticechat.NoticeChatService;
import com.example.pogun.service.notification.NotificationService;
import com.example.pogun.service.payment.CreditService;
import com.example.pogun.service.storage.S3ImageStorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MissingPetServiceTest {

    @Mock
    private PetNoticeRepository petNoticeRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private NoticeBookmarkRepository noticeBookmarkRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private S3ImageStorageService s3ImageStorageService;
    @Mock
    private SimpMessagingTemplate simpMessagingTemplate;
    @Mock
    private AiService aiService;
    @Mock
    private NoticeChatService noticeChatService;
    @Mock
    private AiSourceCacheService aiSourceCacheService;
    @Mock
    private CreditService creditService;

    @InjectMocks
    private MissingPetService missingPetService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void requestManualAnalysis_usesDescriptionOnlyAndCreatesPendingRequest() {
        User author = user();
        PetNotice notice = notice(author, "a".repeat(320));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(author.getFirebaseUid(), "N/A")
        );

        when(userRepository.findByFirebaseUid(author.getFirebaseUid())).thenReturn(Optional.of(author));
        when(petNoticeRepository.findById(notice.getId())).thenReturn(Optional.of(notice));
        when(creditService.useMissingPetAnalysisCredit(notice.getId().toString(), notice.getDescription()))
                .thenReturn(new CreditService.ManualAnalysisCreditUsage(4, "purchase-1", "paw_ai_credits_5_500", 500));
        when(aiService.createMissingPetAnalysisRequest(author, notice, 4, 500, "purchase-1", "paw_ai_credits_5_500"))
                .thenReturn(new MissingPetAnalysisRequestResponse(UUID.randomUUID(), notice.getId(), "PENDING", 4, 500, "purchase-1", "paw_ai_credits_5_500"));

        MissingPetAnalysisRequestResponse response = missingPetService.requestManualAnalysis(notice.getId().toString());

        assertThat(response.remainingCredits()).isEqualTo(4);
        assertThat(response.appliedMaxDescriptionLength()).isEqualTo(500);
        verify(creditService).useMissingPetAnalysisCredit(notice.getId().toString(), notice.getDescription());
    }

    @Test
    void requestManualAnalysis_rejectsWhenDescriptionIsMissing() {
        User author = user();
        PetNotice notice = notice(author, null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(author.getFirebaseUid(), "N/A")
        );

        when(userRepository.findByFirebaseUid(author.getFirebaseUid())).thenReturn(Optional.of(author));
        when(petNoticeRepository.findById(notice.getId())).thenReturn(Optional.of(notice));

        assertThatThrownBy(() -> missingPetService.requestManualAnalysis(notice.getId().toString()))
                .isInstanceOf(ApiException.class)
                .hasMessage("공고 설명이 있어야 AI 분석을 요청할 수 있습니다.");
    }

    private User user() {
        return User.builder()
                .id(UUID.randomUUID())
                .firebaseUid("firebase-uid")
                .email("user@example.com")
                .nickname("tester")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
    }

    private PetNotice notice(User author, String description) {
        return PetNotice.builder()
                .id(UUID.randomUUID())
                .author(author)
                .title("Lost pet")
                .animalType("dog")
                .gender(PetGender.UNKNOWN)
                .description(description)
                .missingDate(Instant.now())
                .missingRegion("Seoul")
                .status(PetNoticeStatus.OPEN)
                .build();
    }
}
