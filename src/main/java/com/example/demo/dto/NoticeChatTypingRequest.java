package com.example.demo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Schema(description = "공고 채팅 입력 중 상태 요청")
public class NoticeChatTypingRequest {
    @Schema(description = "채팅방 ID")
    private UUID roomId;

    @Schema(description = "입력 중 여부", example = "true")
    private Boolean typing;
}
