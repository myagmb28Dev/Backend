package com.example.pogun.service.user;

import com.example.pogun.dto.common.ApiResponse.ApiException;
import com.example.pogun.dto.user.UserFollowResponse;
import com.example.pogun.entity.notification.enums.NotificationPriority;
import com.example.pogun.entity.notification.enums.NotificationTargetType;
import com.example.pogun.entity.notification.enums.NotificationType;
import com.example.pogun.entity.user.User;
import com.example.pogun.entity.user.UserFollow;
import com.example.pogun.repository.user.UserFollowRepository;
import com.example.pogun.repository.user.UserRepository;
import com.example.pogun.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserFollowService {
    private final UserFollowRepository userFollowRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Transactional
    public UserFollowResponse follow(String userId) {
        User follower = getCurrentUser();
        User following = getUser(userId);
        if (follower.getId().equals(following.getId())) {
            throw ApiException.badRequest("SELF_FOLLOW_NOT_ALLOWED", "자기 자신은 팔로우할 수 없습니다.");
        }
        UserFollow follow = userFollowRepository.findByFollowerAndFollowing(follower, following)
                .orElseGet(() -> userFollowRepository.save(UserFollow.builder()
                        .follower(follower)
                        .following(following)
                        .build()));

        notificationService.createAndSendNotification(
                following,
                follower,
                NotificationType.FOLLOWED_ME,
                NotificationTargetType.USER,
                follower.getId(),
                follower.getNickname() + "님이 팔로우했습니다.",
                follower.getNickname() + "님이 선생님을 팔로우했습니다.",
                NotificationPriority.NORMAL,
                "followed-me:" + follower.getId() + ":" + following.getId(),
                Map.of("followerUserId", follower.getId().toString())
        );
        return toResponse(follow.getFollowing(), follow);
    }

    @Transactional
    public void unfollow(String userId) {
        User follower = getCurrentUser();
        User following = getUser(userId);
        userFollowRepository.findByFollowerAndFollowing(follower, following)
                .ifPresent(userFollowRepository::delete);
    }

    @Transactional(readOnly = true)
    public List<UserFollowResponse> getFollowing() {
        User user = getCurrentUser();
        return userFollowRepository.findByFollowerOrderByCreatedAtDesc(user).stream()
                .map(follow -> toResponse(follow.getFollowing(), follow))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UserFollowResponse> getFollowers() {
        User user = getCurrentUser();
        return userFollowRepository.findByFollowingOrderByCreatedAtDesc(user).stream()
                .map(follow -> toResponse(follow.getFollower(), follow))
                .toList();
    }

    private User getCurrentUser() {
        String firebaseUid = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return userRepository.findByFirebaseUid(firebaseUid)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));
    }

    private User getUser(String userId) {
        try {
            return userRepository.findById(UUID.fromString(userId))
                    .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("INVALID_USER_ID", "올바르지 않은 사용자 ID 형식입니다.");
        }
    }

    private UserFollowResponse toResponse(User user, UserFollow follow) {
        return new UserFollowResponse(user.getId(), user.getNickname(), user.getProfileImageUrl(), follow.getCreatedAt());
    }
}
