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
@Table(name = "notice_chat_read_receipts",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_notice_chat_read_receipts_room_reader", columnNames = {"room_id", "reader_id"})
        },
        indexes = {
                @Index(name = "idx_notice_chat_read_receipts_room", columnList = "room_id"),
                @Index(name = "idx_notice_chat_read_receipts_reader", columnList = "reader_id")
        })
public class NoticeChatReadReceipt {
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
    @JoinColumn(name = "room_id", nullable = false, foreignKey = @ForeignKey(name = "fk_notice_chat_read_receipts_room"))
    private NoticeChatRoom room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reader_id", nullable = false, foreignKey = @ForeignKey(name = "fk_notice_chat_read_receipts_reader"))
    private User reader;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_read_message_id", foreignKey = @ForeignKey(name = "fk_notice_chat_read_receipts_message"))
    private NoticeChatMessage lastReadMessage;

    @Builder.Default
    @Column(name = "last_read_room_sequence", nullable = false)
    private Long lastReadRoomSequence = 0L;

    @Column(name = "read_at", nullable = false)
    private Instant readAt;
}
