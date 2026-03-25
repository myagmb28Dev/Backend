package com.example.pogun.dto.community;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
/**
 * API 요청/응답 데이터 전송 객체인 CommunityCommentRequest이다.
 */

@Getter
@Setter
@Schema(description = "댓글 작성 요청")
public class CommunityCommentRequest {
    @NotBlank
    @Schema(description = "댓글 내용", example = "저도 근처에서 본 것 같아요.")
    private String content;
}