package com.example.pogun.service.noticechat;

import com.example.pogun.entity.noticechat.NoticeChatRoom;
import com.example.pogun.entity.user.User;
import com.example.pogun.repository.noticechat.NoticeChatRoomParticipantStateRepository;
import com.example.pogun.repository.noticechat.NoticeChatRoomRepository;
import com.example.pogun.repository.user.UserRepository;
import com.example.pogun.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DirectMessageNotificationEventListener {

    private final UserRepository userRepository;
    private final NoticeChatRoomRepository noticeChatRoomRepository;
    private final NoticeChatRoomParticipantStateRepository participantStateRepository;
    private final NotificationService notificationService;

    @Async("chatNotificationExecutor")
    @EventListener
    public void handle(DirectMessageNotificationEvent event) {
        try {
            User sender = userRepository.findById(event.senderUserId()).orElse(null);
            User recipient = userRepository.findById(event.recipientUserId()).orElse(null);
            NoticeChatRoom room = noticeChatRoomRepository.findById(event.roomId()).orElse(null);
            if (sender == null || recipient == null || room == null || !isNotificationEnabled(room, recipient)) {
                return;
            }
            notificationService.createAndSendDirectMessageNotification(
                    recipient,
                    sender,
                    event.roomId(),
                    event.messageId(),
                    event.replyToRecipient(),
                    event.preview()
            );
        } catch (RuntimeException e) {
            log.warn("direct message notification async failed. roomId={}, messageId={}, reason={}",
                    event.roomId(), event.messageId(), e.getClass().getSimpleName());
        }
    }

    private boolean isNotificationEnabled(NoticeChatRoom room, User recipient) {
        return participantStateRepository.findByRoomAndUser(room, recipient)
                .map(state -> Boolean.TRUE.equals(state.getNotificationEnabled()))
                .orElse(true);
    }
}
