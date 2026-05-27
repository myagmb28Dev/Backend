package com.example.pogun.service.noticechat;

import com.example.pogun.entity.noticechat.NoticeChatRoom;
import com.example.pogun.entity.noticechat.NoticeChatRoomParticipantState;
import com.example.pogun.entity.user.User;
import com.example.pogun.repository.noticechat.NoticeChatRoomParticipantStateRepository;
import com.example.pogun.repository.noticechat.NoticeChatRoomRepository;
import com.example.pogun.repository.user.UserRepository;
import com.example.pogun.service.notification.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DirectMessageNotificationEventListenerTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private NoticeChatRoomRepository noticeChatRoomRepository;
    @Mock
    private NoticeChatRoomParticipantStateRepository participantStateRepository;
    @Mock
    private NotificationService notificationService;

    @Test
    void handle_skipsNotificationWhenRoomNotificationDisabled() {
        DirectMessageNotificationEventListener listener = new DirectMessageNotificationEventListener(
                userRepository,
                noticeChatRoomRepository,
                participantStateRepository,
                notificationService
        );
        User sender = user("sender");
        User recipient = user("recipient");
        NoticeChatRoom room = NoticeChatRoom.builder().id(UUID.randomUUID()).ownerUser(sender).guestUser(recipient).build();
        NoticeChatRoomParticipantState state = NoticeChatRoomParticipantState.builder()
                .room(room)
                .user(recipient)
                .notificationEnabled(false)
                .build();
        DirectMessageNotificationEvent event = event(room.getId(), sender.getId(), recipient.getId());

        when(userRepository.findById(sender.getId())).thenReturn(Optional.of(sender));
        when(userRepository.findById(recipient.getId())).thenReturn(Optional.of(recipient));
        when(noticeChatRoomRepository.findById(room.getId())).thenReturn(Optional.of(room));
        when(participantStateRepository.findByRoomAndUser(room, recipient)).thenReturn(Optional.of(state));

        listener.handle(event);

        verify(notificationService, never()).createAndSendDirectMessageNotification(
                recipient,
                sender,
                event.roomId(),
                event.messageId(),
                event.replyToRecipient(),
                event.preview()
        );
    }

    @Test
    void handle_sendsNotificationWhenRoomNotificationEnabled() {
        DirectMessageNotificationEventListener listener = new DirectMessageNotificationEventListener(
                userRepository,
                noticeChatRoomRepository,
                participantStateRepository,
                notificationService
        );
        User sender = user("sender");
        User recipient = user("recipient");
        NoticeChatRoom room = NoticeChatRoom.builder().id(UUID.randomUUID()).ownerUser(sender).guestUser(recipient).build();
        NoticeChatRoomParticipantState state = NoticeChatRoomParticipantState.builder()
                .room(room)
                .user(recipient)
                .notificationEnabled(true)
                .build();
        DirectMessageNotificationEvent event = event(room.getId(), sender.getId(), recipient.getId());

        when(userRepository.findById(sender.getId())).thenReturn(Optional.of(sender));
        when(userRepository.findById(recipient.getId())).thenReturn(Optional.of(recipient));
        when(noticeChatRoomRepository.findById(room.getId())).thenReturn(Optional.of(room));
        when(participantStateRepository.findByRoomAndUser(room, recipient)).thenReturn(Optional.of(state));

        listener.handle(event);

        verify(notificationService).createAndSendDirectMessageNotification(
                recipient,
                sender,
                event.roomId(),
                event.messageId(),
                event.replyToRecipient(),
                event.preview()
        );
    }

    private User user(String suffix) {
        return User.builder()
                .id(UUID.randomUUID())
                .firebaseUid("firebase-" + suffix)
                .email(suffix + "@local.dev")
                .nickname(suffix)
                .build();
    }

    private DirectMessageNotificationEvent event(UUID roomId, UUID senderId, UUID recipientId) {
        return new DirectMessageNotificationEvent(
                roomId,
                UUID.randomUUID(),
                senderId,
                recipientId,
                false,
                "테스트 메시지"
        );
    }
}
