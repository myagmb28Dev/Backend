package com.example.pogun.repository.admin;

import com.example.pogun.entity.admin.AdminPermissionAssignment;
import com.example.pogun.entity.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AdminPermissionAssignmentRepository extends JpaRepository<AdminPermissionAssignment, UUID> {
    List<AdminPermissionAssignment> findByUser(User user);
}
