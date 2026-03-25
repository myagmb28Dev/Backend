package com.example.pogun.dto.ai;

import io.swagger.v3.oas.annotations.media.Schema;
/**
 * API 요청/응답 데이터 전송 객체인 AiPhotoUploadResponse이다.
 */

@Schema(description = "AI 사진 업로드 응답")
public record AiPhotoUploadResponse(
        String photoId,
        String fileName,
        long size,
        String contentType
) {
}
