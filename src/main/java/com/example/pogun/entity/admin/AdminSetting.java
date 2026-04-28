package com.example.pogun.entity.admin;

import com.example.pogun.entity.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "admin_settings")
public class AdminSetting {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "setting_key", nullable = false, unique = true, length = 100)
    private String settingKey;

    @Column(name = "setting_value", nullable = false, columnDefinition = "text")
    private String settingValue;

    @Column(name = "value_type", nullable = false, length = 20)
    private String valueType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by_user_id", foreignKey = @ForeignKey(name = "fk_admin_settings_updated_by_user"))
    private User updatedByUser;
}
