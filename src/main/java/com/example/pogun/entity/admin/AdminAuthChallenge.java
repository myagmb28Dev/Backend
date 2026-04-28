package com.example.pogun.entity.admin;

import com.example.pogun.entity.admin.enums.AdminAuthChallengeType;
import com.example.pogun.entity.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "admin_auth_challenges", indexes = {
        @Index(name = "idx_admin_auth_challenges_session", columnList = "session_id"),
        @Index(name = "idx_admin_auth_challenges_user", columnList = "user_id"),
        @Index(name = "idx_admin_auth_challenges_expires_at", columnList = "expires_at")
})
public class AdminAuthChallenge {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, foreignKey = @ForeignKey(name = "fk_admin_auth_challenges_session"))
    private AdminSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_admin_auth_challenges_user"))
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 40)
    private AdminAuthChallengeType type;

    @Column(name = "request_json", nullable = false, columnDefinition = "text")
    private String requestJson;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;
}
