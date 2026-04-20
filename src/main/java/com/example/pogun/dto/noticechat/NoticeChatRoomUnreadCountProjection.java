package com.example.pogun.dto.noticechat;

import java.util.UUID;

public record NoticeChatRoomUnreadCountProjection(
        UUID roomId,
        long unreadCount
) {
}
