package com.example.pogun.entity.noticechat;

import com.example.pogun.entity.missingpet.PetNotice;
import com.example.pogun.entity.user.User;
import com.example.pogun.entity.noticechat.enums.NoticeChatRoomStatus;
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
@Table(name = "notice_chat_rooms",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_notice_chat_rooms_notice_owner_guest", columnNames = {"notice_id", "owner_user_id", "guest_user_id"})
        },
        indexes = {
                @Index(name = "idx_notice_chat_rooms_notice", columnList = "notice_id"),
                @Index(name = "idx_notice_chat_rooms_owner", columnList = "owner_user_id"),
                @Index(name = "idx_notice_chat_rooms_guest", columnList = "guest_user_id"),
                @Index(name = "idx_notice_chat_rooms_last_message_at", columnList = "last_message_at")
        })
/**
 * 데이터베이스 테이블과 매핑되는 NoticeChatRoom 엔티티이다.
 */
public class NoticeChatRoom {

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
    @JoinColumn(name = "notice_id", nullable = false, foreignKey = @ForeignKey(name = "fk_notice_chat_rooms_notice"))
    private PetNotice notice;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_notice_chat_rooms_owner"))
    private User ownerUser;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "guest_user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_notice_chat_rooms_guest"))
    private User guestUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private NoticeChatRoomStatus status;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;
}


