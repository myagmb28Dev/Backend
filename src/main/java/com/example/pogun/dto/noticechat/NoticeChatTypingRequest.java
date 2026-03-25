package com.example.pogun.dto.noticechat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;
/**
 * API 요청/응답 데이터 전송 객체인 NoticeChatTypingRequest이다.
 */

@Getter
@Setter
@Schema(description = "공고 채팅 입력 중 상태 요청")
public class NoticeChatTypingRequest {
    @NotNull(message = "roomId는 필수입니다.")
    @Schema(description = "채팅방 ID")
    private UUID roomId;

    @NotNull(message = "typing은 필수입니다.")
    @Schema(description = "입력 중 여부", example = "true")
    private Boolean typing;
}
