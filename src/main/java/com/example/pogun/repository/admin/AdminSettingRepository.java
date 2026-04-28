package com.example.pogun.repository.admin;

import com.example.pogun.entity.admin.AdminSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AdminSettingRepository extends JpaRepository<AdminSetting, UUID> {
    Optional<AdminSetting> findBySettingKey(String settingKey);
}
