package com.example.pogun.service.user;

import com.example.pogun.dto.common.ApiResponse.ApiException;
import com.example.pogun.entity.user.User;
import com.example.pogun.entity.user.UserBlock;
import com.example.pogun.dto.user.UserBlockResponse;
import com.example.pogun.repository.user.UserBlockRepository;
import com.example.pogun.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserBlockService {
    private final UserBlockRepository userBlockRepository;
    private final UserRepository userRepository;

    @Transactional
    public void block(String userId) {
        User blocker = getCurrentUser();
        User blocked = getUser(userId);
        if (blocker.getId().equals(blocked.getId())) {
            throw ApiException.badRequest("SELF_BLOCK_NOT_ALLOWED", "본인은 차단할 수 없습니다.");
        }
        if (userBlockRepository.existsByBlockerAndBlocked(blocker, blocked)) {
            return;
        }
        userBlockRepository.save(UserBlock.builder()
                .blocker(blocker)
                .blocked(blocked)
                .build());
    }

    @Transactional
    public void unblock(String userId) {
        User blocker = getCurrentUser();
        User blocked = getUser(userId);
        userBlockRepository.findByBlockerAndBlocked(blocker, blocked)
                .ifPresent(userBlockRepository::delete);
    }

    @Transactional(readOnly = true)
    public List<UserBlockResponse> listBlockedUsers() {
        User blocker = getCurrentUser();
        return userBlockRepository.findByBlockerOrderByCreatedAtDesc(blocker).stream()
                .map(block -> new UserBlockResponse(
                        block.getBlocked().getId(),
                        block.getBlocked().getNickname(),
                        block.getBlocked().getProfileImageUrl(),
                        block.getCreatedAt()
                ))
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
}
