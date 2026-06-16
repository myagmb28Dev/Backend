package com.example.pogun.service.user;

import com.example.pogun.dto.common.ApiResponse.ApiException;
import com.example.pogun.entity.user.User;
import com.example.pogun.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final UserRepository userRepository;

    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw ApiException.unauthorized("AUTHENTICATION_REQUIRED", "인증이 필요합니다.");
        }
        String firebaseUid = String.valueOf(authentication.getPrincipal());
        return userRepository.findByFirebaseUid(firebaseUid)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));
    }
}
