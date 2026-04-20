package com.example.pogun.dto.storage;

public record StoredImageVariant(
        String originalUrl,
        String webpUrl,
        String mediumUrl,
        String thumbnailUrl,
        String previewUrl
) {
}
