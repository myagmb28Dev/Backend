package com.example.pogun.dto.noticechat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Schema(description = "채팅방 소켓 이벤트 요청")
public class NoticeChatRoomEventRequest {
    @NotNull(message = "roomId는 필수입니다.")
    @Schema(description = "채팅방 ID")
    private UUID roomId;
}
