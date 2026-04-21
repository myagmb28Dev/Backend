package com.example.pogun.dto.storage;

public record StoredMediaVariant(
        String originalUrl,
        String webpUrl,
        String mediumUrl,
        String thumbnailUrl,
        String previewUrl,
        String contentType,
        boolean video
) {
    public String displayUrl() {
        return webpUrl != null ? webpUrl : originalUrl;
    }
}
