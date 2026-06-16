package com.example.pogun.dto.community;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "커뮤니티 댓글 신고 요청")
public class CommunityCommentReportRequest {

    @Schema(description = "신고 사유", example = "ABUSE")
    private String reason;

    @Schema(description = "상세 설명", example = "욕설과 비방이 포함되어 있습니다.")
    private String description;
}
