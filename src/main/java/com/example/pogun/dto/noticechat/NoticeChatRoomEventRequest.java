package com.example.pogun.dto.noticechat;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class NoticeChatRoomEventRequest {
    @NotNull
    private UUID roomId;
}
