package com.example.demo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Schema(description = "공고 채팅 메시지 전송 요청")
public class NoticeChatMessageRequest {
    @Schema(description = "채팅방 ID")
    private UUID roomId;

    @Schema(description = "메시지 내용", example = "근처에서 강아지를 본 것 같아요.")
    private String message;
}
