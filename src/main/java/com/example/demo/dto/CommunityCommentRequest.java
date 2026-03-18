package com.example.demo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "댓글 작성 요청")
public class CommunityCommentRequest {
    @Schema(description = "댓글 내용")
    private String content;
}
