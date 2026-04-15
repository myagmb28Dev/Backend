package com.example.pogun.service.noticechat;

import com.example.pogun.dto.common.ApiResponse.ApiException;
import com.example.pogun.dto.noticechat.NoticeChatMessageEditRequest;
import com.example.pogun.dto.noticechat.NoticeChatMessageProjection;
import com.example.pogun.dto.noticechat.NoticeChatMessageRequest;
import com.example.pogun.dto.noticechat.NoticeChatReadRequest;
import com.example.pogun.dto.storage.StoredImageVariant;
import com.example.pogun.entity.missingpet.PetNotice;
import com.example.pogun.entity.missingpet.enums.PetGender;
import com.example.pogun.entity.missingpet.enums.PetNoticeStatus;
import com.example.pogun.entity.noticechat.NoticeChatMessage;
import com.example.pogun.entity.noticechat.NoticeChatRoom;
import com.example.pogun.entity.noticechat.NoticeChatRoomParticipantState;
import com.example.pogun.entity.noticechat.enums.NoticeChatMessageType;
import com.example.pogun.entity.noticechat.enums.NoticeChatRoomStatus;
import com.example.pogun.entity.user.User;
import com.example.pogun.entity.user.enums.UserRole;
import com.example.pogun.entity.user.enums.UserStatus;
import com.example.pogun.repository.missingpet.PetNoticeRepository;
import com.example.pogun.repository.noticechat.NoticeChatMessageImageRepository;
import com.example.pogun.repository.noticechat.NoticeChatMessageRepository;
import com.example.pogun.repository.noticechat.NoticeChatRoomParticipantStateRepository;
import com.example.pogun.repository.noticechat.NoticeChatRoomRepository;
import com.example.pogun.repository.user.UserBlockRepository;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoticeChatServiceTest {

    @Mock
    private NoticeChatRoomRepository noticeChatRoomRepository;
    @Mock
    private NoticeChatMessageRepository noticeChatMessageRepository;
    @Mock
    private NoticeChatMessageImageRepository noticeChatMessageImageRepository;
    @Mock
    private NoticeChatRoomParticipantStateRepository participantStateRepository;
    @Mock
    private PetNoticeRepository petNoticeRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserBlockRepository userBlockRepository;
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
        lenient().when(participantStateRepository.findByRoomAndUser(any(NoticeChatRoom.class), any(User.class)))
                .thenAnswer(invocation -> Optional.of(participantState(invocation.getArgument(0), invocation.getArgument(1))));
        lenient().when(noticeChatMessageRepository.findUnreadCountsByStateUserAndRooms(any(User.class), any(List.class)))
                .thenReturn(List.of());
        lenient().when(participantStateRepository.findByRoomIn(any(List.class)))
                .thenReturn(List.of());
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
        lenient().when(noticeChatMessageRepository.countByRoomAndSenderUserNotAndDeletedAtIsNull(eq(existingRoom), any(User.class))).thenReturn(0L);
        lenient().when(noticeChatMessageRepository.findTopByRoomAndDeletedAtIsNullOrderByCreatedAtDesc(existingRoom)).thenReturn(Optional.empty());

        var result = noticeChatService.createOrGetRoom(openNotice.getId().toString());

        assertThat(result.created()).isFalse();
        assertThat(result.room().roomId()).isEqualTo(existingRoom.getId());
        assertThat(result.room().noticeId()).isEqualTo(openNotice.getId());
    }

    @Test
    void createOrGetRoom_createsDifferentRoomForSameNoticeAndDifferentGuest() {
        User anotherGuest = user("another-uid", "another@test.dev", "다른 문의자", UserStatus.ACTIVE);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(anotherGuest.getFirebaseUid(), null, List.of())
        );
        NoticeChatRoom newRoom = room(UUID.randomUUID(), openNotice, author, anotherGuest);
        when(userRepository.findByFirebaseUid(anotherGuest.getFirebaseUid())).thenReturn(Optional.of(anotherGuest));
        when(petNoticeRepository.findById(openNotice.getId())).thenReturn(Optional.of(openNotice));
        when(noticeChatRoomRepository.findByNoticeAndOwnerUserAndGuestUser(openNotice, author, anotherGuest))
                .thenReturn(Optional.empty());
        when(noticeChatRoomRepository.save(any(NoticeChatRoom.class))).thenReturn(newRoom);
        lenient().when(noticeChatMessageRepository.countByRoomAndSenderUserNotAndDeletedAtIsNull(eq(newRoom), any(User.class))).thenReturn(0L);
        lenient().when(noticeChatMessageRepository.findTopByRoomAndDeletedAtIsNullOrderByCreatedAtDesc(newRoom)).thenReturn(Optional.empty());

        var result = noticeChatService.createOrGetRoom(openNotice.getId().toString());

        assertThat(result.created()).isTrue();
        assertThat(result.room().roomId()).isEqualTo(newRoom.getId());
        verify(noticeChatRoomRepository).findByNoticeAndOwnerUserAndGuestUser(openNotice, author, anotherGuest);
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
        when(noticeChatRoomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(noticeChatMessageRepository.saveAndFlush(any(NoticeChatMessage.class))).thenReturn(savedMessage);

        var response = noticeChatService.sendMessage(principal(currentUser), request);

        assertThat(response.messageType()).isEqualTo("TEXT");
        ArgumentCaptor<NoticeChatRoom> roomCaptor = ArgumentCaptor.forClass(NoticeChatRoom.class);
        verify(noticeChatRoomRepository).save(roomCaptor.capture());
        assertThat(roomCaptor.getValue().getLastMessageType()).isEqualTo(NoticeChatMessageType.TEXT);
        assertThat(roomCaptor.getValue().getLastMessagePreview()).isEqualTo("텍스트 메시지 전송");
    }

    @Test
    void sendMessage_returnsExistingMessageForDuplicateClientMessageId() {
        UUID roomId = UUID.randomUUID();
        NoticeChatRoom room = room(roomId, openNotice, author, currentUser);
        NoticeChatMessageRequest request = new NoticeChatMessageRequest();
        request.setRoomId(roomId);
        request.setMessage("재전송 메시지");
        request.setClientMessageId("client-message-1");
        NoticeChatMessage existingMessage = NoticeChatMessage.builder()
                .id(UUID.randomUUID())
                .room(room)
                .senderUser(currentUser)
                .messageType(NoticeChatMessageType.TEXT)
                .message("재전송 메시지")
                .clientMessageId("client-message-1")
                .roomSequence(3L)
                .isRead(false)
                .createdAt(Instant.now())
                .build();

        when(userRepository.findByFirebaseUid(currentUser.getFirebaseUid())).thenReturn(Optional.of(currentUser));
        when(noticeChatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(noticeChatMessageRepository.findByRoomAndSenderUserAndClientMessageId(room, currentUser, "client-message-1"))
                .thenReturn(Optional.of(existingMessage));

        var response = noticeChatService.sendMessage(principal(currentUser), request);

        assertThat(response.id()).isEqualTo(existingMessage.getId());
        assertThat(response.clientMessageId()).isEqualTo("client-message-1");
        assertThat(response.roomSequence()).isEqualTo(3L);
    }

    @Test
    void sendMessage_assignsNextSequenceFromLockedRoomSequence() {
        UUID roomId = UUID.randomUUID();
        NoticeChatRoom room = room(roomId, openNotice, author, currentUser);
        room.setLastMessageSequence(41L);
        NoticeChatMessageRequest request = new NoticeChatMessageRequest();
        request.setRoomId(roomId);
        request.setMessage("순번 확인 메시지");

        when(userRepository.findByFirebaseUid(currentUser.getFirebaseUid())).thenReturn(Optional.of(currentUser));
        when(noticeChatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(noticeChatRoomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(noticeChatMessageRepository.saveAndFlush(any(NoticeChatMessage.class))).thenAnswer(invocation -> {
            NoticeChatMessage message = invocation.getArgument(0);
            message.setId(UUID.randomUUID());
            message.setCreatedAt(Instant.now());
            return message;
        });

        var response = noticeChatService.sendMessage(principal(currentUser), request);

        assertThat(response.roomSequence()).isEqualTo(42L);
        assertThat(room.getLastMessageSequence()).isEqualTo(42L);
    }

    @Test
    void websocketReadAck_updatesWatermarkWithoutRoomResponseRecalculation() {
        UUID roomId = UUID.randomUUID();
        NoticeChatRoom room = room(roomId, openNotice, author, currentUser);
        NoticeChatMessage latestOpponentMessage = NoticeChatMessage.builder()
                .id(UUID.randomUUID())
                .room(room)
                .senderUser(author)
                .messageType(NoticeChatMessageType.TEXT)
                .message("읽음 처리 대상")
                .isRead(false)
                .roomSequence(7L)
                .createdAt(Instant.now())
                .build();
        NoticeChatRoomParticipantState currentState = participantState(room, currentUser);
        NoticeChatReadRequest request = new NoticeChatReadRequest();
        request.setRoomId(roomId);
        request.setLastReadRoomSequence(7L);

        when(userRepository.findByFirebaseUid(currentUser.getFirebaseUid())).thenReturn(Optional.of(currentUser));
        when(noticeChatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(noticeChatMessageRepository.findTopByRoomAndSenderUserNotAndDeletedAtIsNullAndRoomSequenceLessThanEqualOrderByRoomSequenceDescCreatedAtDesc(room, currentUser, 7L))
                .thenReturn(Optional.of(latestOpponentMessage));
        when(participantStateRepository.findByRoomAndUser(room, currentUser)).thenReturn(Optional.of(currentState));

        noticeChatService.markRoomAsRead(principal(currentUser), request);

        assertThat(currentState.getLastReadMessage()).isEqualTo(latestOpponentMessage);
        assertThat(currentState.getLastReadRoomSequence()).isEqualTo(7L);
        assertThat(currentState.getLastReadAt()).isNotNull();
        verify(participantStateRepository).save(currentState);
        verify(noticeChatMessageRepository, never()).countByRoomAndSenderUserNotAndDeletedAtIsNull(any(), any());
    }

    @Test
    void updateSettings_savesParticipantPreferences() {
        UUID roomId = UUID.randomUUID();
        NoticeChatRoom room = room(roomId, openNotice, author, currentUser);
        var request = new com.example.pogun.dto.noticechat.NoticeChatRoomSettingsRequest();
        request.setNotificationEnabled(false);
        request.setFavorite(true);
        request.setPinned(true);
        NoticeChatRoomParticipantState currentState = participantState(room, currentUser);
        when(userRepository.findByFirebaseUid(currentUser.getFirebaseUid())).thenReturn(Optional.of(currentUser));
        when(noticeChatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(participantStateRepository.findByRoomAndUser(room, currentUser)).thenReturn(Optional.of(currentState));
        when(participantStateRepository.findByRoomAndUser(room, author)).thenReturn(Optional.of(participantState(room, author)));

        var response = noticeChatService.updateSettings(roomId.toString(), request);

        assertThat(response.notificationEnabled()).isFalse();
        assertThat(response.favorite()).isTrue();
        assertThat(response.pinned()).isTrue();
        verify(participantStateRepository).save(any(NoticeChatRoomParticipantState.class));
    }

    @Test
    void sendImages_storesImageMessageWithOptionalTextAndUsesCommonStorageService() {
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
                .message("이미지와 함께 보낸 글")
                .isRead(false)
                .createdAt(Instant.now())
                .build();

        when(userRepository.findByFirebaseUid(currentUser.getFirebaseUid())).thenReturn(Optional.of(currentUser));
        when(noticeChatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(noticeChatRoomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(localImageStorageService.storeImageVariants(eq("notice-chat"), eq("messages"), eq(currentUser.getId()), any(List.class)))
                .thenReturn(List.of(
                        variant("/uploads/notice-chat/messages/test/one.webp"),
                        variant("/uploads/notice-chat/messages/test/two.webp")
                ));
        when(noticeChatMessageRepository.saveAndFlush(any(NoticeChatMessage.class))).thenReturn(savedMessage);

        var response = noticeChatService.sendImages(roomId.toString(), null, " 이미지와 함께 보낸 글 ", List.of(first, second));

        assertThat(response.messageType()).isEqualTo("IMAGE");
        assertThat(response.message()).isEqualTo("이미지와 함께 보낸 글");
        verify(localImageStorageService).storeImageVariants(eq("notice-chat"), eq("messages"), eq(currentUser.getId()), any(List.class));
    }

    @Test
    void sendImages_allowsEmptyOptionalText() {
        UUID roomId = UUID.randomUUID();
        NoticeChatRoom room = room(roomId, openNotice, author, currentUser);
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        NoticeChatMessage savedMessage = NoticeChatMessage.builder()
                .id(UUID.randomUUID())
                .room(room)
                .senderUser(currentUser)
                .messageType(NoticeChatMessageType.IMAGE)
                .message(null)
                .isRead(false)
                .createdAt(Instant.now())
                .build();

        when(userRepository.findByFirebaseUid(currentUser.getFirebaseUid())).thenReturn(Optional.of(currentUser));
        when(noticeChatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(noticeChatRoomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(localImageStorageService.storeImageVariants(eq("notice-chat"), eq("messages"), eq(currentUser.getId()), any(List.class)))
                .thenReturn(List.of(variant("/uploads/notice-chat/messages/test/one.webp")));
        when(noticeChatMessageRepository.saveAndFlush(any(NoticeChatMessage.class))).thenReturn(savedMessage);

        var response = noticeChatService.sendImages(roomId.toString(), null, "   ", List.of(file));

        assertThat(response.messageType()).isEqualTo("IMAGE");
        assertThat(response.message()).isNull();
    }

    @Test
    void sendImages_storesMp4AsVideoMessageAndPreview() {
        UUID roomId = UUID.randomUUID();
        NoticeChatRoom room = room(roomId, openNotice, author, currentUser);
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        NoticeChatMessage savedMessage = NoticeChatMessage.builder()
                .id(UUID.randomUUID())
                .room(room)
                .senderUser(currentUser)
                .messageType(NoticeChatMessageType.VIDEO)
                .message(null)
                .isRead(false)
                .createdAt(Instant.now())
                .build();

        when(userRepository.findByFirebaseUid(currentUser.getFirebaseUid())).thenReturn(Optional.of(currentUser));
        when(noticeChatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(noticeChatRoomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(localImageStorageService.storeImageVariants(eq("notice-chat"), eq("messages"), eq(currentUser.getId()), any(List.class)))
                .thenReturn(List.of(new StoredImageVariant(
                        "/uploads/notice-chat/messages/test/video.mp4",
                        "/uploads/notice-chat/messages/test/video.mp4",
                        null,
                        null,
                        null
                )));
        when(noticeChatMessageRepository.saveAndFlush(any(NoticeChatMessage.class))).thenReturn(savedMessage);

        var response = noticeChatService.sendImages(roomId.toString(), null, null, List.of(file));

        assertThat(response.messageType()).isEqualTo("VIDEO");
        ArgumentCaptor<NoticeChatRoom> roomCaptor = ArgumentCaptor.forClass(NoticeChatRoom.class);
        verify(noticeChatRoomRepository).save(roomCaptor.capture());
        assertThat(roomCaptor.getValue().getLastMessageType()).isEqualTo(NoticeChatMessageType.VIDEO);
        assertThat(roomCaptor.getValue().getLastMessagePreview()).isEqualTo("동영상을 보냈습니다");
    }

    @Test
    void sendImages_rejectsMessageOverLimit() {
        UUID roomId = UUID.randomUUID();
        NoticeChatRoom room = room(roomId, openNotice, author, currentUser);
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);

        when(userRepository.findByFirebaseUid(currentUser.getFirebaseUid())).thenReturn(Optional.of(currentUser));
        when(noticeChatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> noticeChatService.sendImages(roomId.toString(), null, "a".repeat(2001), List.of(file)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("MESSAGE_TOO_LONG"));
        verify(localImageStorageService, never()).storeImageVariants(any(), any(), any(), any());
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
        when(noticeChatRoomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
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
        lenient().when(noticeChatMessageRepository.countByRoomAndSenderUserNotAndDeletedAtIsNull(room, currentUser)).thenReturn(0L);
        lenient().when(noticeChatMessageRepository.findTopByRoomAndDeletedAtIsNullOrderByCreatedAtDesc(room)).thenReturn(Optional.empty());

        var response = noticeChatService.getRoom(roomId.toString());

        assertThat(response.lastMessageAt()).isNull();
        assertThat(response.lastMessagePreview()).isNull();
        assertThat(response.lastMessageType()).isNull();
        assertThat(response.unreadCount()).isZero();
    }

    @Test
    void getRoom_rejectsNonParticipantFromOtherGuestRoom() {
        User intruder = user("intruder-uid", "intruder@test.dev", "침입자", UserStatus.ACTIVE);
        UUID roomId = UUID.randomUUID();
        NoticeChatRoom userTwoRoom = room(roomId, openNotice, author, currentUser);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(intruder.getFirebaseUid(), null, List.of())
        );
        when(userRepository.findByFirebaseUid(intruder.getFirebaseUid())).thenReturn(Optional.of(intruder));
        when(noticeChatRoomRepository.findById(roomId)).thenReturn(Optional.of(userTwoRoom));

        assertThatThrownBy(() -> noticeChatService.getRoom(roomId.toString()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("CHAT_ROOM_FORBIDDEN"));
    }

    @Test
    void updateMessage_allowsSenderAndBroadcastsEditedMessage() {
        UUID roomId = UUID.randomUUID();
        NoticeChatRoom room = room(roomId, openNotice, author, currentUser);
        NoticeChatMessage message = NoticeChatMessage.builder()
                .id(UUID.randomUUID())
                .room(room)
                .senderUser(currentUser)
                .messageType(NoticeChatMessageType.TEXT)
                .message("수정 전")
                .isRead(false)
                .createdAt(Instant.now())
                .build();
        NoticeChatMessageEditRequest request = new NoticeChatMessageEditRequest();
        request.setMessage("수정 후");

        when(userRepository.findByFirebaseUid(currentUser.getFirebaseUid())).thenReturn(Optional.of(currentUser));
        when(noticeChatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(noticeChatMessageRepository.findById(message.getId())).thenReturn(Optional.of(message));
        when(noticeChatMessageRepository.saveAndFlush(any(NoticeChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(noticeChatMessageRepository.findTopByRoomAndDeletedAtIsNullOrderByCreatedAtDesc(room)).thenReturn(Optional.of(message));

        var response = noticeChatService.updateMessage(roomId.toString(), message.getId().toString(), request);

        assertThat(response.message()).isEqualTo("수정 후");
        assertThat(response.editedAt()).isNotNull();
        verify(noticeChatRoomRepository).save(room);
    }

    @Test
    void updateMessage_rejectsOtherSenderMessage() {
        UUID roomId = UUID.randomUUID();
        NoticeChatRoom room = room(roomId, openNotice, author, currentUser);
        NoticeChatMessage message = NoticeChatMessage.builder()
                .id(UUID.randomUUID())
                .room(room)
                .senderUser(author)
                .messageType(NoticeChatMessageType.TEXT)
                .message("상대 메시지")
                .isRead(false)
                .createdAt(Instant.now())
                .build();
        NoticeChatMessageEditRequest request = new NoticeChatMessageEditRequest();
        request.setMessage("수정 시도");

        when(userRepository.findByFirebaseUid(currentUser.getFirebaseUid())).thenReturn(Optional.of(currentUser));
        when(noticeChatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(noticeChatMessageRepository.findById(message.getId())).thenReturn(Optional.of(message));

        assertThatThrownBy(() -> noticeChatService.updateMessage(roomId.toString(), message.getId().toString(), request))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("CHAT_MESSAGE_FORBIDDEN"));
    }

    @Test
    void deleteMessage_softDeletesSenderMessageAndClearsPreviewWhenNoVisibleMessagesRemain() {
        UUID roomId = UUID.randomUUID();
        NoticeChatRoom room = room(roomId, openNotice, author, currentUser);
        NoticeChatMessage message = NoticeChatMessage.builder()
                .id(UUID.randomUUID())
                .room(room)
                .senderUser(currentUser)
                .messageType(NoticeChatMessageType.TEXT)
                .message("삭제할 메시지")
                .isRead(false)
                .createdAt(Instant.now())
                .build();

        when(userRepository.findByFirebaseUid(currentUser.getFirebaseUid())).thenReturn(Optional.of(currentUser));
        when(noticeChatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(noticeChatMessageRepository.findById(message.getId())).thenReturn(Optional.of(message));
        when(noticeChatMessageRepository.saveAndFlush(any(NoticeChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(noticeChatMessageRepository.findTopByRoomAndDeletedAtIsNullOrderByCreatedAtDesc(room)).thenReturn(Optional.empty());

        var response = noticeChatService.deleteMessage(roomId.toString(), message.getId().toString());

        assertThat(response.deleted()).isTrue();
        assertThat(response.message()).isNull();
        assertThat(room.getLastMessagePreview()).isNull();
        assertThat(room.getLastMessageAt()).isNull();
        verify(noticeChatRoomRepository).save(room);
    }

    @Test
    void deleteMessage_updatesPreviewToPreviousVisibleMessage() {
        UUID roomId = UUID.randomUUID();
        NoticeChatRoom room = room(roomId, openNotice, author, currentUser);
        NoticeChatMessage previous = NoticeChatMessage.builder()
                .id(UUID.randomUUID())
                .room(room)
                .senderUser(author)
                .messageType(NoticeChatMessageType.TEXT)
                .message("직전 메시지")
                .isRead(false)
                .createdAt(Instant.now().minusSeconds(10))
                .build();
        NoticeChatMessage deleted = NoticeChatMessage.builder()
                .id(UUID.randomUUID())
                .room(room)
                .senderUser(currentUser)
                .messageType(NoticeChatMessageType.TEXT)
                .message("삭제할 메시지")
                .isRead(false)
                .createdAt(Instant.now())
                .build();

        when(userRepository.findByFirebaseUid(currentUser.getFirebaseUid())).thenReturn(Optional.of(currentUser));
        when(noticeChatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(noticeChatMessageRepository.findById(deleted.getId())).thenReturn(Optional.of(deleted));
        when(noticeChatMessageRepository.saveAndFlush(any(NoticeChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(noticeChatMessageRepository.findTopByRoomAndDeletedAtIsNullOrderByCreatedAtDesc(room)).thenReturn(Optional.of(previous));

        noticeChatService.deleteMessage(roomId.toString(), deleted.getId().toString());

        assertThat(room.getLastMessagePreview()).isEqualTo("직전 메시지");
        assertThat(room.getLastMessageType()).isEqualTo(NoticeChatMessageType.TEXT);
    }

    @Test
    void getMessages_marksUnreadMessagesAsRead() {
        UUID roomId = UUID.randomUUID();
        NoticeChatRoom room = room(roomId, openNotice, author, currentUser);
        UUID unreadMessageId = UUID.randomUUID();
        NoticeChatMessageProjection unread = new NoticeChatMessageProjection(
                unreadMessageId,
                room.getId(),
                author.getId(),
                author.getNickname(),
                "읽지 않은 메시지",
                NoticeChatMessageType.TEXT,
                false,
                Instant.now(),
                null,
                1L,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        NoticeChatMessage unreadEntity = NoticeChatMessage.builder()
                .id(unreadMessageId)
                .room(room)
                .senderUser(author)
                .messageType(NoticeChatMessageType.TEXT)
                .message("읽지 않은 메시지")
                .isRead(false)
                .roomSequence(1L)
                .createdAt(unread.createdAt())
                .build();

        when(userRepository.findByFirebaseUid(currentUser.getFirebaseUid())).thenReturn(Optional.of(currentUser));
        when(noticeChatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(noticeChatMessageRepository.findVisiblePageRows(eq(room), eq(null), any())).thenReturn(List.of(unread));
        when(noticeChatMessageRepository.findTopByRoomAndSenderUserNotAndDeletedAtIsNullAndRoomSequenceLessThanEqualOrderByRoomSequenceDescCreatedAtDesc(room, currentUser, 1L))
                .thenReturn(Optional.of(unreadEntity));
        when(noticeChatMessageImageRepository.findProjectedByMessageIds(List.of(unreadMessageId))).thenReturn(List.of());

        var response = noticeChatService.getMessages(roomId.toString(), null, null);

        assertThat(response.messages()).hasSize(1);
        assertThat(response.messages().get(0).roomSequence()).isEqualTo(1L);
        verify(participantStateRepository).save(any(NoticeChatRoomParticipantState.class));
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

    private NoticeChatRoomParticipantState participantState(NoticeChatRoom room, User user) {
        return NoticeChatRoomParticipantState.builder()
                .id(UUID.randomUUID())
                .room(room)
                .user(user)
                .notificationEnabled(true)
                .favorite(false)
                .pinned(false)
                .online(false)
                .build();
    }

    private StoredImageVariant variant(String webpUrl) {
        return new StoredImageVariant(webpUrl.replace(".webp", ".jpg"), webpUrl, webpUrl.replace(".webp", "-medium.webp"), webpUrl.replace(".webp", "-thumbnail.webp"), webpUrl.replace(".webp", "-preview.webp"));
    }
}

