package com.example.pogun.entity.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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
@Table(name = "admin_integration_status", indexes = {
        @Index(name = "idx_admin_integration_status_key_created", columnList = "integration_key,created_at")
})
public class AdminIntegrationStatus {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "integration_key", nullable = false, length = 60)
    private String integrationKey;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "message", length = 255)
    private String message;

    @Column(name = "details", columnDefinition = "text")
    private String details;
}
