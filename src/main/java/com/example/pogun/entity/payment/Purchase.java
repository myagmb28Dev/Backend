package com.example.pogun.entity.payment;

import com.example.pogun.entity.payment.enums.PaymentPlatform;
import com.example.pogun.entity.payment.enums.PurchaseStatus;
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
import jakarta.persistence.UniqueConstraint;
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
@Table(name = "purchases", uniqueConstraints = {
        @UniqueConstraint(name = "uk_purchases_transaction_id", columnNames = "transaction_id"),
        @UniqueConstraint(name = "uk_purchases_purchase_token", columnNames = "purchase_token")
}, indexes = {
        @Index(name = "idx_purchases_user_created", columnList = "user_id,created_at"),
        @Index(name = "idx_purchases_platform_status", columnList = "platform,status")
})
public class Purchase {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_purchases_user"))
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 20)
    private PaymentPlatform platform;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PurchaseStatus status;

    @Column(name = "product_id", nullable = false, length = 100)
    private String productId;

    @Column(name = "transaction_id", length = 150)
    private String transactionId;

    @Column(name = "original_transaction_id", length = 150)
    private String originalTransactionId;

    @Column(name = "app_account_token", length = 36)
    private String appAccountToken;

    @Column(name = "purchase_token", length = 512)
    private String purchaseToken;

    @Column(name = "credited_credits", nullable = false)
    private Integer creditedCredits;

    @Builder.Default
    @Column(name = "consumed_credits", nullable = false)
    private Integer consumedCredits = 0;

    @Column(name = "provider_purchase_at")
    private Instant providerPurchaseAt;

    @Column(name = "provider_environment", length = 30)
    private String providerEnvironment;

    @Column(name = "provider_revoked_at")
    private Instant providerRevokedAt;

    @Column(name = "provider_revocation_reason", length = 100)
    private String providerRevocationReason;

    @Column(name = "verification_payload", length = 8000)
    private String verificationPayload;
}
