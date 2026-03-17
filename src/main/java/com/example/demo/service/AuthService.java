package com.example.demo.service;

import com.example.demo.entity.User;
import com.example.demo.entity.enums.UserRole;
import com.example.demo.entity.enums.UserStatus;
import com.example.demo.repository.UserRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final FirebaseAuth firebaseAuth;
    private final UserRepository userRepository;

    @Transactional
    public Map<String, Object> loginOrSignUp(String idToken) throws Exception {
        // 1. Firebase 토큰 검증
        FirebaseToken decodedToken = firebaseAuth.verifyIdToken(idToken);
        String uid = decodedToken.getUid();
        String email = decodedToken.getEmail();
        String name = (String) decodedToken.getClaims().get("name");
        String picture = (String) decodedToken.getClaims().get("picture");

        // 2. DB에서 유저 조회 (없으면 자동 가입)
        User user = userRepository.findByFirebaseUid(uid)
                .map(existingUser -> {
                    // 기존 유저 정보 업데이트 (필요 시)
                    existingUser.setEmail(email);
                    existingUser.setNickname(name != null ? name : "User_" + uid.substring(0, 5));
                    existingUser.setProfileImageUrl(picture);
                    return userRepository.save(existingUser);
                })
                .orElseGet(() -> {
                    // 신규 유저 생성
                    log.info("신규 사용자 가입 진행: {}", email);
                    User newUser = User.builder()
                            .firebaseUid(uid)
                            .email(email)
                            .nickname(name != null ? name : "User_" + uid.substring(0, 5))
                            .profileImageUrl(picture)
                            .role(UserRole.USER)
                            .status(UserStatus.ACTIVE)
                            .build();
                    return userRepository.save(newUser);
                });

        return Map.of(
                "id", user.getId(),
                "firebaseUid", user.getFirebaseUid(),
                "email", user.getEmail(),
                "nickname", user.getNickname(),
                "profileImageUrl", user.getProfileImageUrl() != null ? user.getProfileImageUrl() : "",
                "role", user.getRole().name()
        );
    }

    public Map<String, Object> logout() {
        return Map.of("revoked", true);
    }

    public Map<String, Object> withdraw() {
        return Map.of("status", "WITHDRAWN");
    }

    public Map<String, Object> unlinkSocial(String provider) {
        return Map.of("provider", provider, "unlinked", true);
    }
}
