package com.example.pogun.entity.bookmark;

import com.example.pogun.entity.missingpet.PetNotice;
import com.example.pogun.entity.user.User;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;
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

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "notice_bookmarks",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_notice_bookmarks_user_notice", columnNames = {"user_id", "notice_id"})
        },
        indexes = {
                @Index(name = "idx_notice_bookmarks_user", columnList = "user_id"),
                @Index(name = "idx_notice_bookmarks_notice", columnList = "notice_id")
        })
/**
 * 데이터베이스 테이블과 매핑되는 NoticeBookmark 엔티티이다.
 */
public class NoticeBookmark {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_notice_bookmarks_user"))
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notice_id", nullable = false, foreignKey = @ForeignKey(name = "fk_notice_bookmarks_notice"))
    private PetNotice notice;
}



