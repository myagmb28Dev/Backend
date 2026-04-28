package com.example.pogun.service.adminauth;

import com.example.pogun.entity.admin.AdminAuditLog;
import com.example.pogun.entity.user.User;
import com.example.pogun.repository.admin.AdminAuditLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class AdminAuditService {

    private final AdminAuditLogRepository adminAuditLogRepository;
    private final ObjectMapper objectMapper;
    private final AdminSecurityService adminSecurityService;

    public void log(String action, String targetType, String targetId, Object before, Object after, Map<String, Object> metadata) {
        User actor = null;
        try {
            actor = adminSecurityService.getCurrentAdminUser();
        } catch (RuntimeException ignored) {
        }
        try {
            adminAuditLogRepository.saveAndFlush(AdminAuditLog.builder()
                    .actorUser(actor)
                    .action(action)
                    .targetType(targetType)
                    .targetId(targetId)
                    .ipAddress(resolveRemoteAddress())
                    .beforeState(toJson(before))
                    .afterState(toJson(after))
                    .metadata(toJson(metadata))
                    .build());
        } catch (RuntimeException e) {
            log.warn("Admin audit log persistence failed for action={}", action, e);
        }
    }

    private String resolveRemoteAddress() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }
}
