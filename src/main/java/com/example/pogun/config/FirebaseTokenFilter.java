package com.example.pogun.config;

import com.example.pogun.entity.user.enums.UserRole;
import com.example.pogun.repository.user.UserRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
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
/**
 * 애플리케이션 설정을 담당하는 FirebaseTokenFilter이다.
 */

@Component
@RequiredArgsConstructor
public class
FirebaseTokenFilter extends OncePerRequestFilter {

    private final FirebaseAuth firebaseAuth;
    private final UserRepository userRepository;
    private final ApiErrorResponseWriter apiErrorResponseWriter;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String idToken = header.substring(7);

            try {
                // revoke 여부까지 함께 검사해 로그아웃된 토큰이 보호 API를 다시 통과하지 못하게 한다.
                FirebaseToken decodedToken = firebaseAuth.verifyIdToken(idToken, true);
                String uid = decodedToken.getUid();

                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        uid, null, resolveAuthorities(uid));
                SecurityContextHolder.getContext().setAuthentication(authentication);

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

    private List<SimpleGrantedAuthority> resolveAuthorities(String firebaseUid) {
        // Firebase 토큰 자체에는 우리 서비스 role 이 없으므로 DB 사용자 role 을 다시 읽어 권한을 확정한다.
        UserRole role = userRepository.findByFirebaseUid(firebaseUid)
                .map(user -> user.getRole() != null ? user.getRole() : UserRole.USER)
                .orElse(UserRole.USER);

        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }
}

