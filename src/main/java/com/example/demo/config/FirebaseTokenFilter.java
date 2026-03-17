package com.example.demo.config;

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
import java.util.Collections;

@Component
@RequiredArgsConstructor
public class
FirebaseTokenFilter extends OncePerRequestFilter {

    private final FirebaseAuth firebaseAuth;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String idToken = header.substring(7);

            try {
                // 토큰 검증
                FirebaseToken decodedToken = firebaseAuth.verifyIdToken(idToken);
                String uid = decodedToken.getUid();

                // 인증 객체 생성 (UID를 principal로 사용)
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        uid, null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));

                // SecurityContext에 저장
                SecurityContextHolder.getContext().setAuthentication(authentication);

            } catch (Exception e) {
                // 검증 실패 시 응답 처리 (필요에 따라 에러 메시지 반환 가능)
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("Invalid or expired Firebase ID token.");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
