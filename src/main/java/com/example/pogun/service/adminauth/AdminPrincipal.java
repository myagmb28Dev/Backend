package com.example.pogun.service.adminauth;

import com.example.pogun.entity.admin.enums.AdminPermission;
import com.example.pogun.entity.admin.enums.AdminSessionStage;

import java.util.Set;
import java.util.UUID;

public record AdminPrincipal(
        UUID sessionId,
        UUID userId,
        String firebaseUid,
        String email,
        AdminSessionStage stage,
        Set<AdminPermission> permissions
) {
}
