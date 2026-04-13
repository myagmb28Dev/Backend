package com.example.pogun.dto.noticechat;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "채팅방 개인 설정 요청")
public class NoticeChatRoomSettingsRequest {
    @Schema(description = "알림 사용 여부")
    private Boolean notificationEnabled;

    @Schema(description = "즐겨찾기 여부")
    private Boolean favorite;

    @Schema(description = "상단 고정 여부")
    private Boolean pinned;
}
