package com.example.pogun.entity.noticechat;

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
@Table(name = "notice_chat_room_participant_states",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_notice_chat_room_participant_state", columnNames = {"room_id", "user_id"})
        },
        indexes = {
                @Index(name = "idx_notice_chat_room_participant_room", columnList = "room_id"),
                @Index(name = "idx_notice_chat_room_participant_user", columnList = "user_id"),
                @Index(name = "idx_notice_chat_room_participant_left", columnList = "user_id,left_at")
        })
public class NoticeChatRoomParticipantState {

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
    @JoinColumn(name = "room_id", nullable = false, foreignKey = @ForeignKey(name = "fk_notice_chat_room_participant_room"))
    private NoticeChatRoom room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_notice_chat_room_participant_user"))
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_read_message_id", foreignKey = @ForeignKey(name = "fk_notice_chat_room_participant_last_read_message"))
    private NoticeChatMessage lastReadMessage;

    @Column(name = "last_read_at")
    private Instant lastReadAt;

    @Builder.Default
    @Column(name = "notification_enabled", nullable = false)
    private Boolean notificationEnabled = true;

    @Builder.Default
    @Column(name = "favorite", nullable = false)
    private Boolean favorite = false;

    @Builder.Default
    @Column(name = "pinned", nullable = false)
    private Boolean pinned = false;

    @Column(name = "left_at")
    private Instant leftAt;

    @Column(name = "last_active_at")
    private Instant lastActiveAt;

    @Builder.Default
    @Column(name = "online", nullable = false)
    private Boolean online = false;
}
