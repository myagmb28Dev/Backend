package com.example.pogun.entity.community;

import com.example.pogun.entity.user.User;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.OrderBy;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "community_posts", indexes = {
        @Index(name = "idx_community_posts_created_at", columnList = "created_at"),
        @Index(name = "idx_community_posts_status", columnList = "status"),
        @Index(name = "idx_community_posts_category", columnList = "category"),
        @Index(name = "idx_community_posts_like_count", columnList = "like_count")
})
/**
 * 데이터베이스 테이블과 매핑되는 CommunityPost 엔티티이다.
 */
public class CommunityPost {

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
    @JoinColumn(name = "author_id", nullable = false, foreignKey = @ForeignKey(name = "fk_community_posts_author"))
    private User author;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Builder.Default
    @Column(name = "category", nullable = false, length = 50)
    private String category = "FREE";

    @Builder.Default
    @Column(name = "view_count", nullable = false)
    private Long viewCount = 0L;

    @Builder.Default
    @Column(name = "like_count", nullable = false)
    private Long likeCount = 0L;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CommunityPostStatus status;

    @Column(name = "poll_question", length = 255)
    private String pollQuestion;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "poll_options", columnDefinition = "jsonb")
    private List<String> pollOptions = new ArrayList<>();

    @Builder.Default
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "community_post_tags", joinColumns = @JoinColumn(name = "post_id", foreignKey = @ForeignKey(name = "fk_community_post_tags_post")))
    @OrderColumn(name = "sort_order")
    @Column(name = "tag", nullable = false, length = 50)
    private List<String> tags = new ArrayList<>();

    @Column(name = "poll_ends_at")
    private Instant pollEndsAt;

    @Builder.Default
    @Column(name = "poll_is_multiple_choice", nullable = false)
    private Boolean pollIsMultipleChoice = false;

    @Column(name = "poll_max_choices")
    private Integer pollMaxChoices;

    @Builder.Default
    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<CommunityPostImage> images = new ArrayList<>();
}
