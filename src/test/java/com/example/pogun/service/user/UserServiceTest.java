package com.example.pogun.service.user;

import com.example.pogun.dto.location.RegionResponse;
import com.example.pogun.dto.user.UserLocationUpdateRequest;
import com.example.pogun.dto.user.UserProfileResponse;
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
    void updateLocation_updatesRegionFieldsFromKakaoResult() {
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
                "서울 강남구 역삼동",
                "서울",
                "강남구",
                "역삼동"
        );

        UserLocationUpdateRequest request = new UserLocationUpdateRequest();
        request.setX(127.1086228);
        request.setY(37.4012191);

        when(userRepository.findByFirebaseUid(firebaseUid)).thenReturn(Optional.of(user));
        when(kakaoLocalService.resolveRegion(anyDouble(), anyDouble())).thenReturn(region);
        when(userRepository.save(user)).thenReturn(user);
        when(userSocialAccountRepository.findByUserAndLinkedTrueOrderByCreatedAtAsc(user)).thenReturn(java.util.List.of());

        UserProfileResponse response = userService.updateLocation(request);

        assertThat(user.getRegion()).isEqualTo("서울 강남구 역삼동");
        assertThat(user.getRegionType()).isEqualTo("H");
        assertThat(user.getRegionAddressName()).isEqualTo("서울 강남구 역삼동");
        assertThat(user.getRegion1DepthName()).isEqualTo("서울");
        assertThat(user.getRegion2DepthName()).isEqualTo("강남구");
        assertThat(user.getRegion3DepthName()).isEqualTo("역삼동");
        assertThat(response.region()).isEqualTo("서울 강남구 역삼동");
        assertThat(response.regionInfo()).isNotNull();
        assertThat(response.regionInfo().region2DepthName()).isEqualTo("강남구");
    }
}
