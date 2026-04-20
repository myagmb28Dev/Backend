package com.example.pogun.dto.noticechat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "공고 채팅 메시지 수정 요청")
public class NoticeChatMessageUpdateRequest {
    @NotBlank(message = "message는 필수입니다.")
    @Size(max = 2000, message = "message는 2000자를 초과할 수 없습니다.")
    @Schema(description = "수정할 메시지 내용")
    private String message;
}
