package com.example.pogun.entity.noticechat;

import com.example.pogun.entity.user.User;
import com.example.pogun.entity.noticechat.enums.NoticeChatMessageType;
import jakarta.persistence.CascadeType;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "notice_chat_messages", indexes = {
        @Index(name = "idx_notice_chat_messages_room", columnList = "room_id"),
        @Index(name = "idx_notice_chat_messages_room_created_at", columnList = "room_id,created_at"),
        @Index(name = "idx_notice_chat_messages_room_sequence", columnList = "room_id,room_sequence"),
        @Index(name = "idx_notice_chat_messages_read", columnList = "room_id,is_read"),
        @Index(name = "idx_notice_chat_messages_reply_to", columnList = "reply_to_message_id"),
        @Index(name = "idx_notice_chat_messages_visible_page", columnList = "room_id,deleted_at,room_sequence")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_notice_chat_message_client_id", columnNames = {"room_id", "sender_user_id", "client_message_id"})
})
/**
 * 데이터베이스 테이블과 매핑되는 NoticeChatMessage 엔티티이다.
 */
public class NoticeChatMessage {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false, foreignKey = @ForeignKey(name = "fk_notice_chat_messages_room"))
    private NoticeChatRoom room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_notice_chat_messages_sender"))
    private User senderUser;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 20)
    private NoticeChatMessageType messageType = NoticeChatMessageType.TEXT;

    @Column(name = "message", length = 2000)
    private String message;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reply_to_message_id", foreignKey = @ForeignKey(name = "fk_notice_chat_messages_reply_to"))
    private NoticeChatMessage replyToMessage;

    @Builder.Default
    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<NoticeChatMessageImage> images = new ArrayList<>();

    @Builder.Default
    @Column(name = "is_read", nullable = false)
    private Boolean isRead = false;

    @Column(name = "client_message_id", length = 100)
    private String clientMessageId;

    @Column(name = "room_sequence")
    private Long roomSequence;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}

