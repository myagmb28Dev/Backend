package com.example.pogun.entity.user;

import java.time.Instant;
import java.util.UUID;

import com.example.pogun.entity.user.enums.UserRole;
import com.example.pogun.entity.user.enums.UserAvailabilityStatus;
import com.example.pogun.entity.user.enums.UserStatus;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
/**
 * 데이터베이스 테이블과 매핑되는 User 엔티티이다.
 */

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "users")
public class User {

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

    @Column(name = "firebase_uid", nullable = false, unique = true, length = 128)
    private String firebaseUid;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "nickname", nullable = false, length = 50)
    private String nickname;

    @Column(name = "profile_image_url", length = 1000)
    private String profileImageUrl;

    @Column(name = "phone_number", length = 30)
    private String phoneNumber;

    @Column(name = "region", length = 100)
    private String region;

    @Column(name = "region_type", length = 1)
    private String regionType;

    @Column(name = "region_address_name", length = 100)
    private String regionAddressName;

    @Column(name = "region_1depth_name", length = 100)
    private String region1DepthName;

    @Column(name = "region_2depth_name", length = 100)
    private String region2DepthName;

    @Column(name = "region_3depth_name", length = 100)
    private String region3DepthName;

    @Column(name = "auth_provider", length = 30)
    private String authProvider;

    @Column(name = "last_active_at")
    private Instant lastActiveAt;

    @Builder.Default
    @Column(name = "admin_email_verification_required", nullable = false)
    private boolean adminEmailVerificationRequired = false;

    @Column(name = "admin_email_verified_at")
    private Instant adminEmailVerifiedAt;

    @Column(name = "admin_email_verification_sent_at")
    private Instant adminEmailVerificationSentAt;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "availability_status", nullable = false, length = 20)
    private UserAvailabilityStatus availabilityStatus = UserAvailabilityStatus.ONLINE;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status;
}



