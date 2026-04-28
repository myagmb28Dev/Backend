package com.example.pogun.service.adminauth;

import com.example.pogun.dto.common.ApiResponse.ApiException;
import com.example.pogun.entity.admin.enums.AdminPermission;
import com.example.pogun.entity.user.User;
import com.example.pogun.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminSecurityService {

    private final UserRepository userRepository;

    public AdminPrincipal getCurrentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AdminPrincipal principal)) {
            throw ApiException.unauthorized("ADMIN_SESSION_REQUIRED", "관리자 세션이 필요합니다.");
        }
        return principal;
    }

    @Transactional(readOnly = true)
    public User getCurrentAdminUser() {
        AdminPrincipal principal = getCurrentPrincipal();
        return userRepository.findById(principal.userId())
                .orElseThrow(() -> ApiException.notFound("ADMIN_USER_NOT_FOUND", "관리자 사용자를 찾을 수 없습니다."));
    }

    public Set<AdminPermission> getCurrentPermissions() {
        return getCurrentPrincipal().permissions();
    }

    public void require(AdminPermission permission) {
        if (!getCurrentPermissions().contains(permission)) {
            throw ApiException.forbidden("ADMIN_PERMISSION_DENIED", "해당 관리자 권한이 없습니다.");
        }
    }
}
