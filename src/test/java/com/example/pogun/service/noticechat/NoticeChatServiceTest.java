package com.example.pogun.service.noticechat;

import com.example.pogun.dto.common.ApiResponse.ApiException;
import com.example.pogun.dto.noticechat.NoticeChatMessageRequest;
import com.example.pogun.entity.missingpet.PetNotice;
import com.example.pogun.entity.missingpet.enums.PetGender;
import com.example.pogun.entity.missingpet.enums.PetNoticeStatus;
import com.example.pogun.entity.noticechat.NoticeChatMessage;
import com.example.pogun.entity.noticechat.NoticeChatRoom;
import com.example.pogun.entity.noticechat.enums.NoticeChatMessageType;
import com.example.pogun.entity.noticechat.enums.NoticeChatRoomStatus;
import com.example.pogun.entity.user.User;
import com.example.pogun.entity.user.enums.UserRole;
import com.example.pogun.entity.user.enums.UserStatus;
import com.example.pogun.repository.missingpet.PetNoticeRepository;
import com.example.pogun.repository.noticechat.NoticeChatMessageRepository;
import com.example.pogun.repository.noticechat.NoticeChatRoomRepository;
import com.example.pogun.repository.user.UserRepository;
import com.example.pogun.service.storage.LocalImageStorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoticeChatServiceTest {

    @Mock
    private NoticeChatRoomRepository noticeChatRoomRepository;
    @Mock
    private NoticeChatMessageRepository noticeChatMessageRepository;
    @Mock
    private PetNoticeRepository petNoticeRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SimpMessagingTemplate simpMessagingTemplate;
    @Mock
    private LocalImageStorageService localImageStorageService;

    @InjectMocks
    private NoticeChatService noticeChatService;

    private User currentUser;
    private User author;
    private PetNotice openNotice;

    @BeforeEach
    void setUp() {
        currentUser = user("current-uid", "current@test.dev", "문의자", UserStatus.ACTIVE);
        author = user("author-uid", "author@test.dev", "작성자", UserStatus.ACTIVE);
        openNotice = notice(UUID.randomUUID(), author, PetNoticeStatus.OPEN, false, "실종 공고");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(currentUser.getFirebaseUid(), null, List.of())
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createOrGetRoom_reusesExistingRoomForSameNoticeOwnerGuest() {
        NoticeChatRoom existingRoom = room(UUID.randomUUID(), openNotice, author, currentUser);
        when(userRepository.findByFirebaseUid(currentUser.getFirebaseUid())).thenReturn(Optional.of(currentUser));
        when(petNoticeRepository.findById(openNotice.getId())).thenReturn(Optional.of(openNotice));
        when(noticeChatRoomRepository.findByNoticeAndOwnerUserAndGuestUser(openNotice, author, currentUser))
                .thenReturn(Optional.of(existingRoom));
        when(noticeChatMessageRepository.countByRoomAndSenderUserNotAndIsReadFalse(eq(existingRoom), any(User.class))).thenReturn(0L);
        when(noticeChatMessageRepository.findTopByRoomOrderByCreatedAtDesc(existingRoom)).thenReturn(Optional.empty());

        var result = noticeChatService.createOrGetRoom(openNotice.getId().toString());

        assertThat(result.created()).isFalse();
        assertThat(result.room().roomId()).isEqualTo(existingRoom.getId());
        assertThat(result.room().noticeId()).isEqualTo(openNotice.getId());
    }

    @Test
    void createOrGetRoom_rejectsSelfNotice() {
        PetNotice ownNotice = notice(UUID.randomUUID(), currentUser, PetNoticeStatus.OPEN, false, "내 공고");
        when(userRepository.findByFirebaseUid(currentUser.getFirebaseUid())).thenReturn(Optional.of(currentUser));
        when(petNoticeRepository.findById(ownNotice.getId())).thenReturn(Optional.of(ownNotice));

        assertThatThrownBy(() -> noticeChatService.createOrGetRoom(ownNotice.getId().toString()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("CHAT_ROOM_SELF_NOTICE"));
    }

    @Test
    void createOrGetRoom_rejectsClosedNotice() {
        PetNotice closedNotice = notice(UUID.randomUUID(), author, PetNoticeStatus.CLOSED, false, "종료 공고");
        when(userRepository.findByFirebaseUid(currentUser.getFirebaseUid())).thenReturn(Optional.of(currentUser));
        when(petNoticeRepository.findById(closedNotice.getId())).thenReturn(Optional.of(closedNotice));

        assertThatThrownBy(() -> noticeChatService.createOrGetRoom(closedNotice.getId().toString()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("CHAT_NOTICE_CLOSED"));
    }

    @Test
    void sendMessage_updatesLastMessagePreviewAndType() {
        UUID roomId = UUID.randomUUID();
        NoticeChatRoom room = room(roomId, openNotice, author, currentUser);
        NoticeChatMessageRequest request = new NoticeChatMessageRequest();
        request.setRoomId(roomId);
        request.setMessage("텍스트 메시지 전송");

        NoticeChatMessage savedMessage = NoticeChatMessage.builder()
                .id(UUID.randomUUID())
                .room(room)
                .senderUser(currentUser)
                .messageType(NoticeChatMessageType.TEXT)
                .message("텍스트 메시지 전송")
                .isRead(false)
                .createdAt(Instant.now())
                .build();

        when(userRepository.findByFirebaseUid(currentUser.getFirebaseUid())).thenReturn(Optional.of(currentUser));
        when(noticeChatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(noticeChatMessageRepository.saveAndFlush(any(NoticeChatMessage.class))).thenReturn(savedMessage);

        var response = noticeChatService.sendMessage(principal(currentUser), request);

        assertThat(response.messageType()).isEqualTo("TEXT");
        ArgumentCaptor<NoticeChatRoom> roomCaptor = ArgumentCaptor.forClass(NoticeChatRoom.class);
        verify(noticeChatRoomRepository).save(roomCaptor.capture());
        assertThat(roomCaptor.getValue().getLastMessageType()).isEqualTo(NoticeChatMessageType.TEXT);
        assertThat(roomCaptor.getValue().getLastMessagePreview()).isEqualTo("텍스트 메시지 전송");
    }

    @Test
    void sendImages_storesImageMessageAndUsesCommonStorageService() {
        UUID roomId = UUID.randomUUID();
        NoticeChatRoom room = room(roomId, openNotice, author, currentUser);
        MultipartFile first = org.mockito.Mockito.mock(MultipartFile.class);
        MultipartFile second = org.mockito.Mockito.mock(MultipartFile.class);
        when(first.isEmpty()).thenReturn(false);
        when(second.isEmpty()).thenReturn(false);
        NoticeChatMessage savedMessage = NoticeChatMessage.builder()
                .id(UUID.randomUUID())
                .room(room)
                .senderUser(currentUser)
                .messageType(NoticeChatMessageType.IMAGE)
                .message("")
                .isRead(false)
                .createdAt(Instant.now())
                .build();

        when(userRepository.findByFirebaseUid(currentUser.getFirebaseUid())).thenReturn(Optional.of(currentUser));
        when(noticeChatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(localImageStorageService.storeImages(eq("notice-chat"), eq("messages"), eq(currentUser.getId()), any(List.class)))
                .thenReturn(List.of("/uploads/notice-chat/messages/test/one.webp", "/uploads/notice-chat/messages/test/two.webp"));
        when(noticeChatMessageRepository.saveAndFlush(any(NoticeChatMessage.class))).thenReturn(savedMessage);

        var response = noticeChatService.sendImages(roomId.toString(), null, List.of(first, second));

        assertThat(response.messageType()).isEqualTo("IMAGE");
        verify(localImageStorageService).storeImages(eq("notice-chat"), eq("messages"), eq(currentUser.getId()), any(List.class));
    }

    @Test
    void sendMessage_includesReplySummary() {
        UUID roomId = UUID.randomUUID();
        NoticeChatRoom room = room(roomId, openNotice, author, currentUser);
        NoticeChatMessage parentMessage = NoticeChatMessage.builder()
                .id(UUID.randomUUID())
                .room(room)
                .senderUser(author)
                .messageType(NoticeChatMessageType.TEXT)
                .message("먼저 보낸 메시지")
                .isRead(false)
                .createdAt(Instant.now())
                .build();
        NoticeChatMessageRequest request = new NoticeChatMessageRequest();
        request.setRoomId(roomId);
        request.setMessage("답장합니다");
        request.setReplyToMessageId(parentMessage.getId());

        NoticeChatMessage savedMessage = NoticeChatMessage.builder()
                .id(UUID.randomUUID())
                .room(room)
                .senderUser(currentUser)
                .messageType(NoticeChatMessageType.TEXT)
                .message("답장합니다")
                .replyToMessage(parentMessage)
                .isRead(false)
                .createdAt(Instant.now())
                .build();

        when(userRepository.findByFirebaseUid(currentUser.getFirebaseUid())).thenReturn(Optional.of(currentUser));
        when(noticeChatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(noticeChatMessageRepository.findById(parentMessage.getId())).thenReturn(Optional.of(parentMessage));
        when(noticeChatMessageRepository.saveAndFlush(any(NoticeChatMessage.class))).thenReturn(savedMessage);

        var response = noticeChatService.sendMessage(principal(currentUser), request);

        assertThat(response.reply()).isNotNull();
        assertThat(response.reply().messageId()).isEqualTo(parentMessage.getId());
    }

    @Test
    void getRoom_returnsEmptyRoomSummaryWithoutMessages() {
        UUID roomId = UUID.randomUUID();
        NoticeChatRoom room = room(roomId, openNotice, author, currentUser);
        when(userRepository.findByFirebaseUid(currentUser.getFirebaseUid())).thenReturn(Optional.of(currentUser));
        when(noticeChatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(noticeChatMessageRepository.countByRoomAndSenderUserNotAndIsReadFalse(room, currentUser)).thenReturn(0L);
        when(noticeChatMessageRepository.findTopByRoomOrderByCreatedAtDesc(room)).thenReturn(Optional.empty());

        var response = noticeChatService.getRoom(roomId.toString());

        assertThat(response.lastMessageAt()).isNull();
        assertThat(response.lastMessagePreview()).isNull();
        assertThat(response.lastMessageType()).isNull();
        assertThat(response.unreadCount()).isZero();
    }

    @Test
    void getMessages_marksUnreadMessagesAsRead() {
        UUID roomId = UUID.randomUUID();
        NoticeChatRoom room = room(roomId, openNotice, author, currentUser);
        NoticeChatMessage unread = NoticeChatMessage.builder()
                .id(UUID.randomUUID())
                .room(room)
                .senderUser(author)
                .messageType(NoticeChatMessageType.TEXT)
                .message("읽지 않은 메시지")
                .isRead(false)
                .createdAt(Instant.now())
                .build();

        when(userRepository.findByFirebaseUid(currentUser.getFirebaseUid())).thenReturn(Optional.of(currentUser));
        when(noticeChatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(noticeChatMessageRepository.markUnreadMessagesAsRead(room, currentUser)).thenReturn(1);
        when(noticeChatMessageRepository.findByRoomOrderByCreatedAtAsc(room)).thenReturn(List.of(unread));

        var response = noticeChatService.getMessages(roomId.toString());

        assertThat(response).hasSize(1);
        verify(noticeChatMessageRepository).markUnreadMessagesAsRead(room, currentUser);
    }

    @Test
    void sendMessage_rejectsReplyTargetFromDifferentRoom() {
        NoticeChatRoom currentRoom = room(UUID.randomUUID(), openNotice, author, currentUser);
        NoticeChatRoom otherRoom = room(UUID.randomUUID(), openNotice, author, currentUser);
        NoticeChatMessage foreignMessage = NoticeChatMessage.builder()
                .id(UUID.randomUUID())
                .room(otherRoom)
                .senderUser(author)
                .messageType(NoticeChatMessageType.TEXT)
                .message("다른 방 메시지")
                .build();
        NoticeChatMessageRequest request = new NoticeChatMessageRequest();
        request.setRoomId(currentRoom.getId());
        request.setMessage("답장 시도");
        request.setReplyToMessageId(foreignMessage.getId());

        when(userRepository.findByFirebaseUid(currentUser.getFirebaseUid())).thenReturn(Optional.of(currentUser));
        when(noticeChatRoomRepository.findById(currentRoom.getId())).thenReturn(Optional.of(currentRoom));
        when(noticeChatMessageRepository.findById(foreignMessage.getId())).thenReturn(Optional.of(foreignMessage));

        assertThatThrownBy(() -> noticeChatService.sendMessage(principal(currentUser), request))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("INVALID_REPLY_MESSAGE"));
    }

    private Principal principal(User user) {
        return user::getFirebaseUid;
    }

    private User user(String firebaseUid, String email, String nickname, UserStatus status) {
        return User.builder()
                .id(UUID.randomUUID())
                .firebaseUid(firebaseUid)
                .email(email)
                .nickname(nickname)
                .role(UserRole.USER)
                .status(status)
                .build();
    }

    private PetNotice notice(UUID id, User author, PetNoticeStatus status, boolean hidden, String title) {
        return PetNotice.builder()
                .id(id)
                .author(author)
                .title(title)
                .animalType("DOG")
                .gender(PetGender.UNKNOWN)
                .missingDate(Instant.now())
                .missingRegion("Seoul")
                .status(status)
                .hidden(hidden)
                .build();
    }

    private NoticeChatRoom room(UUID id, PetNotice notice, User owner, User guest) {
        return NoticeChatRoom.builder()
                .id(id)
                .notice(notice)
                .ownerUser(owner)
                .guestUser(guest)
                .status(NoticeChatRoomStatus.OPEN)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}
