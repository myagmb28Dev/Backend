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

    @Schema(description = "사용자별 채팅방 표시 이름")
    private String customRoomName;

    @Schema(description = "사용자별 채팅방 표시 이름 초기화 여부")
    private Boolean clearCustomRoomName;

    @Schema(description = "사용자별 채팅방 썸네일 초기화 여부")
    private Boolean clearCustomThumbnailUrl;
}
