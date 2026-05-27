package com.example.pogun.service.noticechat;

import java.util.UUID;

public record DirectMessageNotificationEvent(
        UUID roomId,
        UUID messageId,
        UUID senderUserId,
        UUID recipientUserId,
        boolean replyToRecipient,
        String preview
) {
}
