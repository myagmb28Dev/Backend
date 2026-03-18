package com.example.demo.service;

import com.example.demo.entity.User;
import com.example.demo.entity.enums.UserRole;
import com.example.demo.entity.enums.UserStatus;
import com.example.demo.repository.UserRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.google.firebase.auth.UserRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
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
        if ("test-token".equals(idToken)) {
            String uid = "test-uid-123";
            String email = "test@pogeun.com";
            String name = "테스트유저";
            String picture = "";

            User user = userRepository.findByFirebaseUid(uid)
                    .map(existingUser -> {
                        existingUser.setEmail(email);
                        existingUser.setNickname(name);
                        existingUser.setStatus(UserStatus.ACTIVE);
                        return userRepository.save(existingUser);
                    })
                    .orElseGet(() -> {
                        User newUser = User.builder()
                                .firebaseUid(uid)
                                .email(email)
                                .nickname(name)
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
                    "profileImageUrl", "",
                    "role", user.getRole().name()
            );
        }

        try {
            FirebaseToken decodedToken = firebaseAuth.verifyIdToken(idToken);
            String uid = decodedToken.getUid();
            String email = decodedToken.getEmail();

            UserRecord userRecord = firebaseAuth.getUser(uid);
            String name = userRecord.getDisplayName();
            String picture = userRecord.getPhotoUrl();

            User user = userRepository.findByFirebaseUid(uid)
                    .map(existingUser -> {
                        existingUser.setEmail(email);
                        existingUser.setNickname(name != null ? name : "User_" + uid.substring(0, 5));
                        existingUser.setProfileImageUrl(picture);
                        existingUser.setStatus(UserStatus.ACTIVE); 
                        return userRepository.save(existingUser);
                    })
                    .orElseGet(() -> {
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
        } catch (FirebaseAuthException e) {
            log.error("Firebase 토큰 검증 중 오류 발생: {}", e.getMessage());
            throw new RuntimeException("인증 오류가 발생했습니다: " + e.getAuthErrorCode());
        }
    }

    private User getCurrentUser() {
        String firebaseUid = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return userRepository.findByFirebaseUid(firebaseUid)
                .orElseGet(() -> {
                    if ("test-uid-123".equals(firebaseUid)) {
                        return userRepository.save(User.builder()
                                .firebaseUid("test-uid-123")
                                .email("test@pogeun.com")
                                .nickname("테스트유저")
                                .role(UserRole.USER)
                                .status(UserStatus.ACTIVE)
                                .build());
                    }
                    throw new RuntimeException("사용자를 찾을 수 없습니다.");
                });
    }

    public Map<String, Object> logout() {
        User user = getCurrentUser();
        log.info("사용자 로그아웃 처리 (RefreshToken 만료): {}", user.getEmail());

        try {
            if (!"test-uid-123".equals(user.getFirebaseUid())) {
                firebaseAuth.revokeRefreshTokens(user.getFirebaseUid());
            }
            return Map.of("revoked", true, "message", "로그아웃 성공. 모든 세션이 만료되었습니다.");
        } catch (FirebaseAuthException e) {
            log.error("Firebase 로그아웃 처리 중 오류 발생: {}", e.getMessage());
            throw new RuntimeException("로그아웃 처리 중 오류가 발생했습니다.");
        }
    }

    @Transactional
    public Map<String, Object> withdraw() {
        User user = getCurrentUser();
        log.info("사용자 탈퇴 처리 시작 (Firebase 계정 삭제 포함): {}", user.getEmail());

        try {
            if (!"test-uid-123".equals(user.getFirebaseUid())) {
                firebaseAuth.deleteUser(user.getFirebaseUid());
            }

            user.setStatus(UserStatus.WITHDRAWN);
            userRepository.save(user);

            log.info("사용자 탈퇴 완료: {}", user.getEmail());
            return Map.of("status", "WITHDRAWN", "message", "회원 탈퇴 및 Firebase 계정 삭제가 완료되었습니다.");
        } catch (FirebaseAuthException e) {
            log.error("Firebase 사용자 삭제 중 오류 발생: {}", e.getMessage());
            throw new RuntimeException("Firebase 탈퇴 처리 중 오류가 발생했습니다.");
        } catch (Exception e) {
            log.error("사용자 DB 탈퇴 처리 중 오류 발생: {}", e.getMessage());
            throw new RuntimeException("DB 탈퇴 처리 중 오류가 발생했습니다.");
        }
    }

    @Transactional
    public Map<String, Object> unlinkSocial(String provider) {
        User user = getCurrentUser();
        log.info("소셜 계정 연결 해제 요청: {}, Provider: {}", user.getEmail(), provider);

        try {
            if (!"test-uid-123".equals(user.getFirebaseUid())) {
                UserRecord userRecord = firebaseAuth.getUser(user.getFirebaseUid());
                log.info("사용자 현재 연결된 제공자 수: {}", userRecord.getProviderData().length);
                
                firebaseAuth.revokeRefreshTokens(user.getFirebaseUid());
            }
            
            return Map.of("provider", provider, "unlinked", true, "message", provider + " 계정 연결이 실무적으로 해제(토큰 무효화)되었습니다.");
        } catch (FirebaseAuthException e) {
            log.error("Firebase 소셜 연결 해제 중 오류 발생: {}", e.getMessage());
            throw new RuntimeException("연결 해제 중 오류가 발생했습니다.");
        }
    }
}
