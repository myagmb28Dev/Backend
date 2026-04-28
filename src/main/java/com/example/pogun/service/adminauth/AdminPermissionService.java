package com.example.pogun.service.adminauth;

import com.example.pogun.entity.admin.AdminPermissionAssignment;
import com.example.pogun.entity.admin.enums.AdminPermission;
import com.example.pogun.entity.user.User;
import com.example.pogun.entity.user.enums.UserRole;
import com.example.pogun.repository.admin.AdminPermissionAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminPermissionService {

    private final AdminPermissionAssignmentRepository adminPermissionAssignmentRepository;

    @Transactional(readOnly = true)
    public Set<AdminPermission> getPermissions(User user) {
        if (user == null || user.getRole() != UserRole.ADMIN) {
            return Set.of();
        }
        List<AdminPermissionAssignment> assignments = adminPermissionAssignmentRepository.findByUser(user);
        if (assignments.isEmpty()) {
            return EnumSet.allOf(AdminPermission.class);
        }
        EnumSet<AdminPermission> granted = EnumSet.noneOf(AdminPermission.class);
        assignments.stream()
                .filter(assignment -> Boolean.TRUE.equals(assignment.getGranted()))
                .map(AdminPermissionAssignment::getPermission)
                .forEach(granted::add);
        return granted;
    }

    @Transactional
    public void ensureDefaults(User user) {
        if (user == null || user.getRole() != UserRole.ADMIN) {
            return;
        }
        if (!adminPermissionAssignmentRepository.findByUser(user).isEmpty()) {
            return;
        }
        Arrays.stream(AdminPermission.values()).forEach(permission ->
                adminPermissionAssignmentRepository.save(AdminPermissionAssignment.builder()
                        .user(user)
                        .permission(permission)
                        .granted(true)
                        .build()));
        adminPermissionAssignmentRepository.flush();
    }
}
