package com.example.pogun.entity.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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
@Table(name = "apple_app_store_notifications", uniqueConstraints = {
        @UniqueConstraint(name = "uk_apple_app_store_notifications_uuid", columnNames = "notification_uuid")
}, indexes = {
        @Index(name = "idx_apple_notifications_type_created", columnList = "notification_type,created_at"),
        @Index(name = "idx_apple_notifications_transaction", columnList = "transaction_id")
})
public class AppleAppStoreNotification {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    @Column(name = "notification_uuid", nullable = false, length = 100)
    private String notificationUuid;

    @Column(name = "notification_type", nullable = false, length = 80)
    private String notificationType;

    @Column(name = "subtype", length = 80)
    private String subtype;

    @Column(name = "transaction_id", length = 150)
    private String transactionId;

    @Column(name = "original_transaction_id", length = 150)
    private String originalTransactionId;

    @Column(name = "processing_result", nullable = false, length = 80)
    private String processingResult;

    @Column(name = "signed_payload", columnDefinition = "text")
    private String signedPayload;
}
