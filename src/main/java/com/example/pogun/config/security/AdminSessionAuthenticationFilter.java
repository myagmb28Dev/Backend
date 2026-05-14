package com.example.pogun.config;

import com.example.pogun.entity.admin.AdminSession;
import com.example.pogun.entity.admin.enums.AdminSessionStage;
import com.example.pogun.entity.user.User;
import com.example.pogun.repository.admin.AdminPasskeyRepository;
import com.example.pogun.service.adminauth.AdminPermissionService;
import com.example.pogun.service.adminauth.AdminPrincipal;
import com.example.pogun.service.adminauth.AdminSessionTokenService;
import com.example.pogun.repository.user.UserRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AdminSessionAuthenticationFilter extends OncePerRequestFilter {

    private final AdminSessionTokenService adminSessionTokenService;
    private final AdminPermissionService adminPermissionService;
    private final AdminPasskeyRepository adminPasskeyRepository;
    private final UserRepository userRepository;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/admin/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);
        Optional<AdminSession> resolved = adminSessionTokenService.resolve(token);
        if (resolved.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        AdminSession session = resolved.get();
        User user = userRepository.findById(session.getUser().getId()).orElse(null);
        if (user == null) {
            filterChain.doFilter(request, response);
            return;
        }

        if (session.getStage() == AdminSessionStage.AUTHENTICATED && !adminPasskeyRepository.existsByUser(user)) {
            adminSessionTokenService.revoke(session);
            SecurityContextHolder.clearContext();
            filterChain.doFilter(request, response);
            return;
        }

        var permissions = adminPermissionService.getPermissions(user);

        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN_CONSOLE"));
        if (session.getStage() == AdminSessionStage.AUTHENTICATED) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
        permissions
                .forEach(permission -> authorities.add(new SimpleGrantedAuthority("ADMIN_PERMISSION_" + permission.name())));

        AdminPrincipal principal = new AdminPrincipal(
                session.getId(),
                user.getId(),
                user.getFirebaseUid(),
                user.getEmail(),
                session.getStage(),
                permissions
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, token, authorities)
        );
        adminSessionTokenService.touch(session);
        filterChain.doFilter(request, response);
    }
}
