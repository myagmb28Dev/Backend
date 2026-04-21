package com.example.pogun.service.user;

import com.example.pogun.dto.common.ApiResponse.ApiException;

import com.example.pogun.dto.location.RegionResponse;
import com.example.pogun.dto.user.UpdateProfileRequest;
import com.example.pogun.dto.user.UserAvailabilityResponse;
import com.example.pogun.dto.user.UserAvailabilityUpdateRequest;
import com.example.pogun.dto.user.UserCommunityPostSummaryResponse;
import com.example.pogun.dto.user.UserPetNoticeSummaryResponse;
import com.example.pogun.dto.user.UserProfileResponse;
import com.example.pogun.entity.community.CommunityPost;
import com.example.pogun.entity.community.enums.CommunityPostStatus;
import com.example.pogun.entity.missingpet.PetNotice;
import com.example.pogun.entity.user.User;
import com.example.pogun.entity.user.UserSocialAccount;
import com.example.pogun.entity.user.enums.UserAvailabilityStatus;
import com.example.pogun.repository.community.CommunityPostRepository;
import com.example.pogun.repository.missingpet.PetNoticeRepository;
import com.example.pogun.repository.user.UserRepository;
import com.example.pogun.repository.user.UserSocialAccountRepository;
import com.example.pogun.service.storage.LocalImageStorageService;
import org.springframework.web.multipart.MultipartFile;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
/**
 * 도메인 비즈니스 로직을 담당하는 UserService이다.
 */

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PetNoticeRepository petNoticeRepository;
    private final CommunityPostRepository communityPostRepository;
    private final UserSocialAccountRepository userSocialAccountRepository;
    private final LocalImageStorageService localImageStorageService;

    private User getCurrentUser() {
        String firebaseUid = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return userRepository.findByFirebaseUid(firebaseUid)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));
    }

    public UserProfileResponse getProfile() {
        User user = getCurrentUser();
        return getProfileResponse(user);
    }

    public UserProfileResponse updateProfile(UpdateProfileRequest request) {
        return updateProfile(request, null);
    }

    public UserProfileResponse updateProfile(UpdateProfileRequest request, MultipartFile profileImage) {
        User user = getCurrentUser();

        if (request.getNickname() != null && !request.getNickname().isBlank()) {
            user.setNickname(request.getNickname().trim());
        }
        if (request.getPhoneNumber() != null) {
            user.setPhoneNumber(request.getPhoneNumber().trim());
        }
        if (request.getRegion() != null) {
            user.setRegion(request.getRegion().trim());
        }
        if (profileImage != null && !profileImage.isEmpty()) {
            user.setProfileImageUrl(localImageStorageService.storeImage("profile", "users", user.getId(), profileImage));
        } else if (request.getProfileImageUrl() != null) {
            user.setProfileImageUrl(request.getProfileImageUrl().trim());
        }

        User saved = userRepository.save(user);
        return getProfileResponse(saved);
    }

    public UserAvailabilityResponse getAvailability() {
        User user = getCurrentUser();
        return new UserAvailabilityResponse(
                resolveAvailabilityStatus(user).name(),
                user.getLastActiveAt()
        );
    }

    public UserAvailabilityResponse updateAvailability(UserAvailabilityUpdateRequest request) {
        User user = getCurrentUser();
        if (request == null || request.getAvailabilityStatus() == null || request.getAvailabilityStatus().isBlank()) {
            throw ApiException.badRequest("MISSING_AVAILABILITY_STATUS", "가용 상태는 필수입니다.");
        }
        UserAvailabilityStatus availabilityStatus;
        try {
            availabilityStatus = UserAvailabilityStatus.valueOf(request.getAvailabilityStatus().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("INVALID_AVAILABILITY_STATUS", "올바르지 않은 가용 상태입니다.");
        }
        user.setAvailabilityStatus(availabilityStatus);
        User saved = userRepository.save(user);
        return new UserAvailabilityResponse(resolveAvailabilityStatus(saved).name(), saved.getLastActiveAt());
    }

    public List<UserPetNoticeSummaryResponse> myPetNotices() {
        User user = getCurrentUser();
        return petNoticeRepository.findByAuthorOrderByCreatedAtDesc(user).stream()
                .map(this::toPetNoticeSummary)
                .toList();
    }

    public List<UserCommunityPostSummaryResponse> myCommunityPosts() {
        User user = getCurrentUser();
        return communityPostRepository.findByAuthorIdAndStatusNotOrderByCreatedAtDesc(user.getId(), CommunityPostStatus.DELETED).stream()
                .map(this::toCommunityPostSummary)
                .toList();
    }

    // 프로필 응답에서 null 을 기본값으로 정규화해 프론트가 필드 존재 여부를 따로 분기하지 않게 한다.
    private UserProfileResponse getProfileResponse(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getProfileImageUrl() != null ? user.getProfileImageUrl() : "https://cdn.ex.com/profile/default.png",
                user.getPhoneNumber() != null ? user.getPhoneNumber() : "",
                user.getRegion() != null ? user.getRegion() : "",
                buildRegionResponse(user),
                user.getAuthProvider() != null ? user.getAuthProvider() : "GOOGLE",
                getLinkedProviders(user),
                user.getRole().name(),
                user.getStatus().name(),
                resolveAvailabilityStatus(user).name()
        );
    }

    private UserAvailabilityStatus resolveAvailabilityStatus(User user) {
        return user.getAvailabilityStatus() != null ? user.getAvailabilityStatus() : UserAvailabilityStatus.ONLINE;
    }

    private RegionResponse buildRegionResponse(User user) {
        if (user.getRegionAddressName() == null || user.getRegionAddressName().isBlank()) {
            return null;
        }
        return new RegionResponse(
                user.getRegionType(),
                user.getRegionAddressName(),
                user.getRegion1DepthName(),
                user.getRegion2DepthName(),
                user.getRegion3DepthName()
        );
    }

    private List<String> getLinkedProviders(User user) {
        List<String> linkedProviders = userSocialAccountRepository.findByUserAndLinkedTrueOrderByCreatedAtAsc(user).stream()
                .map(UserSocialAccount::getProvider)
                .toList();
        if (!linkedProviders.isEmpty()) {
            return linkedProviders;
        }
        if (user.getAuthProvider() != null && !user.getAuthProvider().isBlank()) {
            return List.of(user.getAuthProvider());
        }
        return List.of();
    }

    private UserPetNoticeSummaryResponse toPetNoticeSummary(PetNotice notice) {
        return new UserPetNoticeSummaryResponse(
                notice.getId(),
                notice.getTitle(),
                notice.getAnimalType(),
                notice.getBreed(),
                notice.getMissingDate(),
                notice.getMissingRegion(),
                notice.getStatus().name(),
                notice.getViewCount(),
                notice.getCreatedAt()
        );
    }

    private UserCommunityPostSummaryResponse toCommunityPostSummary(CommunityPost post) {
        return new UserCommunityPostSummaryResponse(
                post.getId(),
                post.getTitle(),
                post.getStatus().name(),
                post.getViewCount(),
                post.getCreatedAt()
        );
    }
}

