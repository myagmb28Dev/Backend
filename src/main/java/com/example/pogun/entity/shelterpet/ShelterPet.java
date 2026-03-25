package com.example.pogun.entity.shelterpet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "shelter_pets", indexes = {
        @Index(name = "idx_shelter_pets_region", columnList = "region"),
        @Index(name = "idx_shelter_pets_breed", columnList = "breed"),
        @Index(name = "idx_shelter_pets_status", columnList = "status")
})
/**
 * 데이터베이스 테이블과 매핑되는 ShelterPet 엔티티이다.
 */
public class ShelterPet {

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 100)
    private String id;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "source", nullable = false, length = 100)
    private String source;

    @Column(name = "region", nullable = false, length = 100)
    private String region;

    @Column(name = "breed", nullable = false, length = 100)
    private String breed;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "reward_amount")
    private Integer rewardAmount;

    @Column(name = "contact_phone", length = 50)
    private String contactPhone;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "images", columnDefinition = "jsonb")
    private List<String> images = new ArrayList<>();

    @Builder.Default
    @Column(name = "view_count", nullable = false)
    private Integer viewCount = 0;

    @Column(name = "analysis_status", length = 30)
    private String analysisStatus;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "analysis_features", columnDefinition = "jsonb")
    private List<String> analysisFeatures = new ArrayList<>();

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "similar_notice_ids", columnDefinition = "jsonb")
    private List<String> similarNoticeIds = new ArrayList<>();
}
