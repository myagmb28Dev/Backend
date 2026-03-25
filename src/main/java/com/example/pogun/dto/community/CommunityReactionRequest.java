package com.example.pogun.dto.community;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
/**
 * API 요청/응답 데이터 전송 객체인 CommunityReactionRequest이다.
 */

@Getter
@Setter
@Schema(description = "커뮤니티 반응 요청")
public class CommunityReactionRequest {
    @NotBlank
    @Schema(description = "반응 타입", example = "LIKE")
    private String reaction;
}

