package com.example.pogun.dto.community;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "커뮤니티 댓글 수정 응답")
public record CommunityCommentUpdateResponse(
        UUID id,
        UUID postId,
        boolean updated
) {
}
