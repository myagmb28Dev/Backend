package com.example.pogun.config.security;

import com.example.pogun.config.web.RequestHostResolver;
import com.example.pogun.entity.user.enums.UserRole;
import com.example.pogun.entity.user.enums.UserStatus;
import com.example.pogun.entity.user.User;
import com.example.pogun.repository.user.UserRepository;
import com.example.pogun.service.auth.FirebaseIdentityService;
import com.example.pogun.service.user.UserPresenceService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
/**
 * 애플리케이션 설정을 담당하는 FirebaseTokenFilter이다.
 */

@Component
@RequiredArgsConstructor
public class FirebaseTokenFilter extends OncePerRequestFilter {

    private final FirebaseIdentityService firebaseIdentityService;
    private final UserRepository userRepository;
    private final UserPresenceService userPresenceService;
    private final ApiErrorResponseWriter apiErrorResponseWriter;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (request.getRequestURI().startsWith("/api/admin/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String idToken = header.substring(7);

            try {
                // revoke 여부까지 함께 검사해 로그아웃된 토큰이 보호 API를 다시 통과하지 못하게 한다.
                String uid = firebaseIdentityService.verifyIdToken(idToken, true).uid();
                Optional<User> user = userRepository.findByFirebaseUid(uid);
                if (user.isEmpty() && shouldRequireCompletedRegistration(request)) {
                    SecurityContextHolder.clearContext();
                    apiErrorResponseWriter.write(
                            response,
                            org.springframework.http.HttpStatus.FORBIDDEN,
                            "ONBOARDING_REQUIRED",
                            "회원가입 완료 후 이용할 수 있습니다.",
                            null
                    );
                    return;
                }
                if (user.isPresent() && shouldBlockInactiveUser(request) && user.get().getStatus() != null && user.get().getStatus() != UserStatus.ACTIVE) {
                    SecurityContextHolder.clearContext();
                    UserStatus status = user.get().getStatus();
                    apiErrorResponseWriter.write(
                            response,
                            org.springframework.http.HttpStatus.FORBIDDEN,
                            status == UserStatus.BANNED ? "USER_BANNED" : "USER_WITHDRAWN",
                            status == UserStatus.BANNED ? "제재된 사용자는 이용할 수 없습니다." : "탈퇴한 사용자는 이용할 수 없습니다.",
                            null
                    );
                    return;
                }

                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    uid, idToken, resolveAuthorities(user));
                SecurityContextHolder.getContext().setAuthentication(authentication);
                try {
                    userPresenceService.touchFromAuthenticationSafely(uid, RequestHostResolver.resolve(request));
                } catch (RuntimeException ignored) {
                    // presence 갱신 실패는 인증 실패가 아니므로 요청은 계속 처리한다.
                }

            } catch (Exception e) {
                SecurityContextHolder.clearContext();
                apiErrorResponseWriter.write(
                        response,
                        org.springframework.http.HttpStatus.UNAUTHORIZED,
                        "INVALID_TOKEN",
                        "유효하지 않거나 만료된 Firebase ID 토큰입니다.",
                        null
                );
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean shouldRequireCompletedRegistration(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();

        if ("POST".equalsIgnoreCase(method) && "/api/auth/onboarding/complete".equals(path)) {
            return false;
        }
        if ("POST".equalsIgnoreCase(method) && "/api/auth/login".equals(path)) {
            return false;
        }
        return path.startsWith("/api/");
    }

    private boolean shouldBlockInactiveUser(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        if (!path.startsWith("/api/")) {
            return false;
        }
        if ("POST".equalsIgnoreCase(method) && "/api/auth/logout".equals(path)) {
            return false;
        }
        return true;
    }

    private List<SimpleGrantedAuthority> resolveAuthorities(Optional<User> resolvedUser) {
        // Firebase 토큰 자체에는 우리 서비스 role 이 없으므로 DB 사용자 role 을 다시 읽어 권한을 확정한다.
        UserRole role = resolvedUser
                .map(user -> user.getRole() != null ? user.getRole() : UserRole.USER)
                .orElse(UserRole.USER);

        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }
}
