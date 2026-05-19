package com.example.pogun.entity.missingpet;

import com.example.pogun.entity.user.User;
import java.time.Instant;
import java.util.UUID;

import com.example.pogun.entity.missingpet.enums.PetGender;
import com.example.pogun.entity.missingpet.enums.PetNoticeStatus;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.CascadeType;
import jakarta.persistence.OrderBy;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "pet_notices", indexes = {
        @Index(name = "idx_pet_notices_status", columnList = "status"),
        @Index(name = "idx_pet_notices_missing_date", columnList = "missing_date"),
        @Index(name = "idx_pet_notices_missing_region", columnList = "missing_region"),
        @Index(name = "idx_pet_notices_created_at", columnList = "created_at"),
        @Index(name = "idx_pet_notices_status_updated_at", columnList = "status,updated_at"),
        @Index(name = "idx_pet_notices_hidden_status_missing_date_created_at", columnList = "is_hidden,status,missing_date,created_at"),
        @Index(name = "idx_pet_notices_hidden", columnList = "is_hidden"),
        @Index(name = "idx_pet_notices_author_created_at", columnList = "author_id,created_at")
})
/**
 * 데이터베이스 테이블과 매핑되는 PetNotice 엔티티이다.
 */
public class PetNotice {

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
    @JoinColumn(name = "author_id", nullable = false, foreignKey = @ForeignKey(name = "fk_pet_notices_author"))
    private User author;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "animal_type", nullable = false, length = 50)
    private String animalType;

    @Column(name = "breed", length = 100)
    private String breed;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", nullable = false, length = 20)
    private PetGender gender;

    @Column(name = "age")
    private Integer age;

    @Column(name = "color", length = 120)
    private String color;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "missing_date", nullable = false)
    private Instant missingDate;

    @Column(name = "missing_region", nullable = false, length = 100)
    private String missingRegion;

    @Column(name = "missing_address", length = 255)
    private String missingAddress;

    @Column(name = "reward_amount")
    private Integer rewardAmount;

    @Column(name = "contact_phone", length = 30)
    private String contactPhone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PetNoticeStatus status;

    @Builder.Default
    @Column(name = "view_count", nullable = false)
    private Long viewCount = 0L;

    @Builder.Default
    @Column(name = "is_hidden", nullable = false)
    private Boolean hidden = false;

    @Builder.Default
    @OneToMany(mappedBy = "notice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<PetNoticeImage> images = new ArrayList<>();
}

