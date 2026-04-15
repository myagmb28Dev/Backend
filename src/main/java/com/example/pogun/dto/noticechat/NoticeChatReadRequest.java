package com.example.pogun.dto.noticechat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Schema(description = "채팅방 읽음 소켓 이벤트 요청")
public class NoticeChatReadRequest {
    @NotNull(message = "roomId는 필수입니다.")
    @Schema(description = "채팅방 ID")
    private UUID roomId;

    @Schema(description = "마지막으로 읽은 메시지 ID")
    private UUID lastReadMessageId;

    @Schema(description = "마지막으로 읽은 채팅방 순번")
    private Long lastReadRoomSequence;
}
