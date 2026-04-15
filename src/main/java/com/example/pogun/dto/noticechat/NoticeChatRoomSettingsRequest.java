package com.example.pogun.dto.noticechat;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NoticeChatRoomSettingsRequest {
    private Boolean notificationEnabled;
    private Boolean favorite;
    private Boolean pinned;
}
