package com.example.pogun.entity.noticechat;

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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "notice_chat_message_images", indexes = {
        @Index(name = "idx_notice_chat_message_images_message", columnList = "message_id"),
        @Index(name = "idx_notice_chat_message_images_order", columnList = "message_id,display_order")
})
public class NoticeChatMessageImage {
    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false, foreignKey = @ForeignKey(name = "fk_notice_chat_message_images_message"))
    private NoticeChatMessage message;

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    @Column(name = "original_url", length = 500)
    private String originalUrl;

    @Column(name = "webp_url", length = 500)
    private String webpUrl;

    @Column(name = "medium_url", length = 500)
    private String mediumUrl;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Column(name = "preview_url", length = 500)
    private String previewUrl;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;
}
