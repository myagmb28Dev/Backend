package com.example.pogun.dto.community;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
/**
 * API 요청/응답 데이터 전송 객체인 CommunityPostRequest이다.
 */

@Getter
@Setter
@Schema(description = "커뮤니티 게시글 생성 요청")
public class CommunityPostRequest {
    @NotBlank
    @Size(max = 150)
    @Schema(description = "제목", example = "우리 강아지가 지금 비보잉을 하고 있어요!")
    private String title;

    @NotBlank
    @Size(max = 5000)
    @Schema(description = "내용", example = "너무 귀여워서 공유합니다. 여러분의 반려동물은 어떤 재주가 있나요?")
    private String content;

    @Size(max = 50)
    @Schema(description = "카테고리", example = "FREE")
    private String category;

    @Size(max = 255)
    @Schema(description = "투표 질문", example = "어떤 사료가 더 맛있어 보이나요?")
    private String pollQuestion;

    @Schema(description = "투표 옵션 목록", example = "[\"A사료\", \"B사료\"]")
    private List<@Size(max = 100) String> pollOptions;

    @Schema(description = "태그 목록", example = "[\"강아지\", \"산책\", \"일상\"]")
    private List<@Size(max = 50) String> tags;

}

