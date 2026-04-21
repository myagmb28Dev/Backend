package com.example.pogun.config;

import com.example.pogun.dto.common.ApiResponse.ApiException;
import com.example.pogun.entity.missingpet.PetNotice;
import com.example.pogun.entity.missingpet.enums.PetGender;
import com.example.pogun.entity.missingpet.enums.PetNoticeStatus;
import com.example.pogun.entity.noticechat.NoticeChatRoom;
import com.example.pogun.entity.noticechat.enums.NoticeChatRoomStatus;
import com.example.pogun.entity.user.User;
import com.example.pogun.entity.user.enums.UserRole;
import com.example.pogun.entity.user.enums.UserStatus;
import com.example.pogun.repository.noticechat.NoticeChatRoomRepository;
import com.example.pogun.repository.user.UserRepository;
import com.example.pogun.service.auth.FirebaseIdentityService;
import com.example.pogun.service.user.UserPresenceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StompAuthChannelInterceptorTest {

    @Mock
    private FirebaseIdentityService firebaseIdentityService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private NoticeChatRoomRepository noticeChatRoomRepository;
        @Mock
        private UserPresenceService userPresenceService;

    @Test
    void preSend_setsPrincipalOnConnect() throws Exception {
                StompAuthChannelInterceptor interceptor = new StompAuthChannelInterceptor(firebaseIdentityService, userRepository, noticeChatRoomRepository, userPresenceService);
        when(firebaseIdentityService.verifyIdToken("token-value", true))
                .thenReturn(new FirebaseIdentityService.FirebaseIdentity("firebase-uid", "tester@local.dev", "Tester", null, "password", List.of(), Map.of()));

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", "Bearer token-value");
        accessor.setSessionAttributes(new java.util.HashMap<>());
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, null);

        assertThat(result).isNotNull();
        verify(firebaseIdentityService).verifyIdToken("token-value", true);
    }

    @Test
    void preSend_rejectsSubscriptionForNonParticipant() {
                StompAuthChannelInterceptor interceptor = new StompAuthChannelInterceptor(firebaseIdentityService, userRepository, noticeChatRoomRepository, userPresenceService);
        User currentUser = user("current-uid", "current@test.dev", UserStatus.ACTIVE);
        User author = user("author-uid", "author@test.dev", UserStatus.ACTIVE);
        User guest = user("guest-uid", "guest@test.dev", UserStatus.ACTIVE);
        NoticeChatRoom room = NoticeChatRoom.builder()
                .id(UUID.randomUUID())
                .notice(PetNotice.builder()
                        .id(UUID.randomUUID())
                        .author(author)
                        .title("실종 공고")
                        .animalType("DOG")
                        .gender(PetGender.UNKNOWN)
                        .missingDate(Instant.now())
                        .missingRegion("Seoul")
                        .status(PetNoticeStatus.OPEN)
                        .hidden(false)
                        .build())
                .ownerUser(author)
                .guestUser(guest)
                .status(NoticeChatRoomStatus.OPEN)
                .build();

        when(userRepository.findByFirebaseUid("current-uid")).thenReturn(Optional.of(currentUser));
        when(noticeChatRoomRepository.findById(room.getId())).thenReturn(Optional.of(room));

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/chat/users/" + currentUser.getId() + "/rooms/" + room.getId());
        accessor.setUser(new WebSocketPrincipal("current-uid"));
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("CHAT_ROOM_FORBIDDEN"));
    }

    private User user(String firebaseUid, String email, UserStatus status) {
        return User.builder()
                .id(UUID.randomUUID())
                .firebaseUid(firebaseUid)
                .email(email)
                .nickname(email)
                .role(UserRole.USER)
                .status(status)
                .build();
    }
}
