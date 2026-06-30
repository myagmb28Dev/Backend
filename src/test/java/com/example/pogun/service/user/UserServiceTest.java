package com.example.pogun.service.user;

import com.example.pogun.dto.location.RegionResponse;
import com.example.pogun.dto.user.UserLocationUpdateRequest;
import com.example.pogun.dto.user.UserPetNoticeSummaryResponse;
import com.example.pogun.dto.user.UserProfileResponse;
import com.example.pogun.entity.missingpet.PetNotice;
import com.example.pogun.entity.user.User;
import com.example.pogun.entity.user.enums.UserRole;
import com.example.pogun.entity.user.enums.UserStatus;
import com.example.pogun.repository.community.CommunityPostRepository;
import com.example.pogun.repository.missingpet.PetNoticeRepository;
import com.example.pogun.repository.user.UserRepository;
import com.example.pogun.repository.user.UserSocialAccountRepository;
import com.example.pogun.service.location.KakaoLocalService;
import com.example.pogun.service.noticechat.NoticeChatService;
import com.example.pogun.service.storage.S3ImageStorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PetNoticeRepository petNoticeRepository;
    @Mock
    private CommunityPostRepository communityPostRepository;
    @Mock
    private UserSocialAccountRepository userSocialAccountRepository;
    @Mock
    private S3ImageStorageService s3ImageStorageService;
    @Mock
    private NoticeChatService noticeChatService;
    @Mock
    private UserPresenceService userPresenceService;
    @Mock
    private KakaoLocalService kakaoLocalService;

    @InjectMocks
    private UserService userService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void updateLocation_updatesRegionFieldsFromResolvedRegion() {
        String firebaseUid = "firebase-uid-1";
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(firebaseUid, "N/A")
        );

        User user = User.builder()
                .id(UUID.randomUUID())
                .firebaseUid(firebaseUid)
                .email("user@example.com")
                .nickname("tester")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();

        RegionResponse region = new RegionResponse(
                "H",
                "Seoul Gangnam Yeoksam",
                "Seoul",
                "Gangnam-gu",
                "Yeoksam-dong"
        );

        UserLocationUpdateRequest request = new UserLocationUpdateRequest();
        request.setX(127.1086228);
        request.setY(37.4012191);

        when(userRepository.findByFirebaseUid(firebaseUid)).thenReturn(Optional.of(user));
        when(kakaoLocalService.resolveRegion(anyDouble(), anyDouble())).thenReturn(region);
        when(userRepository.save(user)).thenReturn(user);
        when(userSocialAccountRepository.findByUserAndLinkedTrueOrderByCreatedAtAsc(user)).thenReturn(List.of());

        UserProfileResponse response = userService.updateLocation(request);

        assertThat(user.getRegion()).isEqualTo("Seoul Gangnam Yeoksam");
        assertThat(user.getRegionType()).isEqualTo("H");
        assertThat(user.getRegionAddressName()).isEqualTo("Seoul Gangnam Yeoksam");
        assertThat(user.getRegion1DepthName()).isEqualTo("Seoul");
        assertThat(user.getRegion2DepthName()).isEqualTo("Gangnam-gu");
        assertThat(user.getRegion3DepthName()).isEqualTo("Yeoksam-dong");
        assertThat(response.region()).isEqualTo("Seoul Gangnam Yeoksam");
        assertThat(response.regionInfo()).isEqualTo(region);
    }

    @Test
    void getProfile_defaultsLegacyNullRoleAndStatus() {
        String firebaseUid = "firebase-uid-legacy";
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(firebaseUid, "N/A")
        );

        User user = User.builder()
                .id(UUID.randomUUID())
                .firebaseUid(firebaseUid)
                .email("legacy@example.com")
                .nickname("legacy-user")
                .role(null)
                .status(null)
                .build();

        when(userRepository.findByFirebaseUid(firebaseUid)).thenReturn(Optional.of(user));
        when(userSocialAccountRepository.findByUserAndLinkedTrueOrderByCreatedAtAsc(user)).thenReturn(List.of());

        UserProfileResponse response = userService.getProfile();

        assertThat(response.role()).isEqualTo(UserRole.USER.name());
        assertThat(response.status()).isEqualTo(UserStatus.ACTIVE.name());
    }

    @Test
    void myPetNotices_defaultsLegacyNullStatusAndViewCount() {
        String firebaseUid = "firebase-uid-notices";
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(firebaseUid, "N/A")
        );

        User user = User.builder()
                .id(UUID.randomUUID())
                .firebaseUid(firebaseUid)
                .email("notice-owner@example.com")
                .nickname("notice-owner")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
        PetNotice notice = PetNotice.builder()
                .id(UUID.randomUUID())
                .author(user)
                .title("Legacy notice")
                .animalType("dog")
                .missingDate(Instant.parse("2026-06-30T09:30:00Z"))
                .missingRegion("Seoul")
                .status(null)
                .viewCount(null)
                .build();

        when(userRepository.findByFirebaseUid(firebaseUid)).thenReturn(Optional.of(user));
        when(petNoticeRepository.findByAuthorOrderByCreatedAtDesc(user)).thenReturn(List.of(notice));

        List<UserPetNoticeSummaryResponse> response = userService.myPetNotices();

        assertThat(response).hasSize(1);
        assertThat(response.get(0).status()).isEqualTo("OPEN");
        assertThat(response.get(0).viewCount()).isZero();
        assertThat(response.get(0).imageUrls()).isEmpty();
    }
}
