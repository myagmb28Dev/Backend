package com.example.pogun.dto.noticechat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;
/**
 * API 요청/응답 데이터 전송 객체인 NoticeChatMessageRequest이다.
 */

@Getter
@Setter
@Schema(description = "공고 채팅 메시지 전송 요청")
public class NoticeChatMessageRequest {
    @NotNull(message = "roomId는 필수입니다.")
    @Schema(description = "채팅방 ID")
    private UUID roomId;

    @NotBlank(message = "message는 필수입니다.")
    @Size(max = 2000, message = "message는 2000자를 초과할 수 없습니다.")
    @Schema(description = "메시지 내용", example = "근처에서 강아지를 본 것 같아요.")
    private String message;
}
