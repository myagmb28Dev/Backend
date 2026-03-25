package com.example.pogun.entity.notification;

import com.example.pogun.entity.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
@Table(name = "user_fcm_tokens",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_user_fcm_tokens_token", columnNames = {"token"})
        },
        indexes = {
                @Index(name = "idx_user_fcm_tokens_user", columnList = "user_id"),
                @Index(name = "idx_user_fcm_tokens_active", columnList = "is_active")
        })
/**
 * 데이터베이스 테이블과 매핑되는 UserFcmToken 엔티티이다.
 */
public class UserFcmToken {

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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_user_fcm_tokens_user"))
    private User user;

    @Column(name = "token", nullable = false, length = 512)
    private String token;

    @Column(name = "platform", length = 30)
    private String platform;

    @Column(name = "device_id", length = 255)
    private String deviceId;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean active = true;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;
}

