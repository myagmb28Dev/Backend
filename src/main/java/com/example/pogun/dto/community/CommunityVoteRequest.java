package com.example.pogun.dto.community;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
/**
 * API 요청/응답 데이터 전송 객체인 CommunityVoteRequest이다.
 */

@Getter
@Setter
@Schema(description = "커뮤니티 투표 참여 요청")
public class CommunityVoteRequest {
    @NotBlank
    @Schema(description = "선택한 옵션", example = "A사료")
    private String option;
}

