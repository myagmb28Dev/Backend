package com.example.pogun.service.noticechat;

import com.example.pogun.dto.common.ApiResponse.ApiException;
import com.example.pogun.dto.noticechat.NoticeChatMessageRequest;
import com.example.pogun.dto.noticechat.NoticeChatMessageUpdateRequest;
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
import com.example.pogun.repository.noticechat.NoticeChatReadReceiptRepository;
import com.example.pogun.repository.noticechat.NoticeChatRoomParticipantStateRepository;
import com.example.pogun.repository.noticechat.NoticeChatRoomRepository;
import com.example.pogun.repository.user.UserBlockRepository;
import com.example.pogun.repository.user.UserRepository;
import com.example.pogun.service.notification.NotificationService;
import com.example.pogun.service.storage.S3ImageStorageService;
import com.example.pogun.service.user.UserPresenceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoticeChatServiceTest {

    private static final String S3_BASE_URL = "https://2026capstone-ktw.s3.ap-northeast-2.amazonaws.com";

    @Mock
    private NoticeChatRoomRepository noticeChatRoomRepository;
    @Mock
    private NoticeChatMessageRepository noticeChatMessageRepository;
    @Mock
    private NoticeChatMessageImageRepository noticeChatMessageImageRepository;
    @Mock
    private NoticeChatReadReceiptRepository noticeChatReadReceiptRepository;
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
    private S3ImageStorageService s3ImageStorageService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private UserPresenceService userPresenceService;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

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
        lenient().when(userPresenceService.snapshot(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            return new UserPresenceService.PresenceSnapshot(
                    user != null && user.getStatus() == UserStatus.ACTIVE
                            ? com.example.pogun.entity.user.enums.UserAvailabilityStatus.ONLINE
                            : com.example.pogun.entity.user.enums.UserAvailabilityStatus.OFFLINE,
                    user != null && user.getStatus() == UserStatus.ACTIVE
                            ? com.example.pogun.entity.user.enums.UserAvailabilityStatus.ONLINE
                            : com.example.pogun.entity.user.enums.UserAvailabilityStatus.OFFLINE,
                    user != null && user.getStatus() == UserStatus.ACTIVE ? "connected" : "disconnected",
                    user != null ? user.getLastActiveAt() : null
            );
        });
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
        when(noticeChatMessageRepository.countUnreadByWatermark(eq(existingRoom), any(User.class), eq(0L))).thenReturn(0L);
        when(noticeChatMessageRepository.findTopByRoomAndDeletedAtIsNullOrderByRoomSequenceDescCreatedAtDesc(existingRoom)).thenReturn(Optional.empty());

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
        when(noticeChatRoomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(noticeChatMessageRepository.save(any(NoticeChatMessage.class))).thenReturn(savedMessage);

        var response = noticeChatService.sendMessage(principal(currentUser), request);

        assertThat(response.messageType()).isEqualTo("TEXT");
        ArgumentCaptor<NoticeChatRoom> roomCaptor = ArgumentCaptor.forClass(NoticeChatRoom.class);
        verify(noticeChatRoomRepository).save(roomCaptor.capture());
        assertThat(roomCaptor.getValue().getLastMessageType()).isEqualTo(NoticeChatMessageType.TEXT);
        assertThat(roomCaptor.getValue().getLastMessagePreview()).isEqualTo("텍스트 메시지 전송");
    }

    @Test
    void sendMessage_publishesAsyncDirectMessageNotificationEvent() {
        UUID roomId = UUID.randomUUID();
        NoticeChatRoom room = room(roomId, openNotice, author, currentUser);
        NoticeChatMessageRequest request = new NoticeChatMessageRequest();
        request.setRoomId(roomId);
        request.setMessage("비동기 알림 테스트");

        NoticeChatMessage savedMessage = NoticeChatMessage.builder()
                .id(UUID.randomUUID())
                .room(room)
                .senderUser(currentUser)
                .messageType(NoticeChatMessageType.TEXT)
                .message("비동기 알림 테스트")
                .isRead(false)
                .createdAt(Instant.now())
                .build();

        when(userRepository.findByFirebaseUid(currentUser.getFirebaseUid())).thenReturn(Optional.of(currentUser));
        when(noticeChatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(noticeChatRoomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(noticeChatMessageRepository.save(any(NoticeChatMessage.class))).thenReturn(savedMessage);

        noticeChatService.sendMessage(principal(currentUser), request);

        verify(applicationEventPublisher).publishEvent(any(DirectMessageNotificationEvent.class));
        verify(notificationService, never()).createAndSendNotification(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
        );
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

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(simpMessagingTemplate, atLeastOnce())
                .convertAndSend(eq("/topic/chat/users/" + currentUser.getId() + "/rooms/" + roomId), payloadCaptor.capture());

        @SuppressWarnings("unchecked")
        Map<String, Object> ackPayload = payloadCaptor.getAllValues().stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .filter(map -> "MESSAGE_ACK".equals(map.get("type")))
                .findFirst()
                .orElse(null);

        assertThat(ackPayload).isNotNull();
        assertThat(ackPayload.get("clientMessageId")).isEqualTo("client-message-1");
        assertThat(ackPayload.get("duplicate")).isEqualTo(true);
    }

    @Test
    void sendMessage_sendsAckForNewClientMessageId() {
        UUID roomId = UUID.randomUUID();
        NoticeChatRoom room = room(roomId, openNotice, author, currentUser);
        NoticeChatMessageRequest request = new NoticeChatMessageRequest();
        request.setRoomId(roomId);
        request.setMessage("ACK 대상 메시지");
        request.setClientMessageId("client-message-ack-1");

        NoticeChatMessage savedMessage = NoticeChatMessage.builder()
                .id(UUID.randomUUID())
                .room(room)
                .senderUser(currentUser)
                .messageType(NoticeChatMessageType.TEXT)
                .message("ACK 대상 메시지")
                .clientMessageId("client-message-ack-1")
                .roomSequence(11L)
                .isRead(false)
                .createdAt(Instant.now())
                .build();

        when(userRepository.findByFirebaseUid(currentUser.getFirebaseUid())).thenReturn(Optional.of(currentUser));
        when(noticeChatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(noticeChatRoomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(noticeChatMessageRepository.findByRoomAndSenderUserAndClientMessageId(room, currentUser, "client-message-ack-1"))
                .thenReturn(Optional.empty());
        when(noticeChatMessageRepository.save(any(NoticeChatMessage.class))).thenReturn(savedMessage);

        noticeChatService.sendMessage(principal(currentUser), request);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(simpMessagingTemplate, atLeastOnce())
                .convertAndSend(eq("/topic/chat/users/" + currentUser.getId() + "/rooms/" + roomId), payloadCaptor.capture());

        @SuppressWarnings("unchecked")
        Map<String, Object> ackPayload = payloadCaptor.getAllValues().stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .filter(map -> "MESSAGE_ACK".equals(map.get("type")))
                .findFirst()
                .orElse(null);

        assertThat(ackPayload).isNotNull();
        assertThat(ackPayload.get("clientMessageId")).isEqualTo("client-message-ack-1");
        assertThat(ackPayload.get("duplicate")).isEqualTo(false);
    }

    @Test
    void publishMessageNack_sendsNackPayloadToUserRoomTopic() {
        UUID roomId = UUID.randomUUID();
        when(userRepository.findByFirebaseUid(currentUser.getFirebaseUid())).thenReturn(Optional.of(currentUser));

        noticeChatService.publishMessageNack(
                currentUser.getFirebaseUid(),
                roomId,
                "client-message-nack-1",
                "MESSAGE_SEND_FAILED",
                "전송 실패"
        );

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(simpMessagingTemplate).convertAndSend(eq("/topic/chat/users/" + currentUser.getId() + "/rooms/" + roomId), payloadCaptor.capture());

        assertThat(payloadCaptor.getValue()).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> nackPayload = (Map<String, Object>) payloadCaptor.getValue();
        assertThat(nackPayload.get("type")).isEqualTo("MESSAGE_NACK");
        assertThat(nackPayload.get("clientMessageId")).isEqualTo("client-message-nack-1");
        assertThat(nackPayload.get("errorCode")).isEqualTo("MESSAGE_SEND_FAILED");
    }

    @Test
    void updateSettings_savesParticipantPreferences() {
        UUID roomId = UUID.randomUUID();
        NoticeChatRoom room = room(roomId, openNotice, author, currentUser);
        var request = new com.example.pogun.dto.noticechat.NoticeChatRoomSettingsRequest();
        request.setNotificationEnabled(false);
        request.setFavorite(true);
        request.setPinned(true);
        request.setCustomRoomName(" 구조 요청 ");
        NoticeChatRoomParticipantState currentState = participantState(room, currentUser);
        when(userRepository.findByFirebaseUid(currentUser.getFirebaseUid())).thenReturn(Optional.of(currentUser));
        when(noticeChatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(participantStateRepository.findByRoomAndUser(room, currentUser)).thenReturn(Optional.of(currentState));

        var response = noticeChatService.updateSettings(roomId.toString(), request);

        assertThat(response.notificationEnabled()).isFalse();
        assertThat(response.favorite()).isTrue();
        assertThat(response.pinned()).isTrue();
        assertThat(response.customRoomName()).isEqualTo("구조 요청");
        assertThat(response.customThumbnailUrl()).isNull();
        assertThat(response.displayRoomName()).isEqualTo("구조 요청");
        assertThat(response.displayThumbnailUrl()).isNull();
        verify(participantStateRepository).save(any(NoticeChatRoomParticipantState.class));
    }

    @Test
    void updateSettings_clearsCustomRoomDisplayOptions() {
        UUID roomId = UUID.randomUUID();
        NoticeChatRoom room = room(roomId, openNotice, author, currentUser);
        var request = new com.example.pogun.dto.noticechat.NoticeChatRoomSettingsRequest();
        request.setClearCustomRoomName(true);
        request.setClearCustomThumbnailUrl(true);
        NoticeChatRoomParticipantState currentState = participantState(room, currentUser);
        currentState.setCustomRoomName("사용자 설정 이름");
        currentState.setCustomThumbnailUrl("https://local.test/custom.webp");
        when(userRepository.findByFirebaseUid(currentUser.getFirebaseUid())).thenReturn(Optional.of(currentUser));
        when(noticeChatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(participantStateRepository.findByRoomAndUser(room, currentUser)).thenReturn(Optional.of(currentState));

        var response = noticeChatService.updateSettings(roomId.toString(), request);

        assertThat(response.customRoomName()).isNull();
        assertThat(response.customThumbnailUrl()).isNull();
        assertThat(response.displayRoomName()).isEqualTo("실종 공고 · 작성자");
    }

    @Test
    void updateRoomThumbnail_storesUploadedImageAsCustomThumbnail() {
        UUID roomId = UUID.randomUUID();
        NoticeChatRoom room = room(roomId, openNotice, author, currentUser);
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        NoticeChatRoomParticipantState currentState = participantState(room, currentUser);
        when(userRepository.findByFirebaseUid(currentUser.getFirebaseUid())).thenReturn(Optional.of(currentUser));
        when(noticeChatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(participantStateRepository.findByRoomAndUser(room, currentUser)).thenReturn(Optional.of(currentState));
        when(s3ImageStorageService.storeImageVariant("notice-chat", "rooms", currentUser.getId(), file))
                .thenReturn(variant(s3Url("uploads/notice-chat/rooms/test/room.webp")));

        var response = noticeChatService.updateRoomThumbnail(roomId.toString(), file);

        assertThat(response.customThumbnailUrl()).endsWith("-thumbnail.webp");
        assertThat(response.displayThumbnailUrl()).endsWith("-thumbnail.webp");
        verify(participantStateRepository).save(currentState);
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
        when(s3ImageStorageService.storeImageVariants(eq("notice-chat"), eq("messages"), eq(currentUser.getId()), anyList()))
                .thenReturn(List.of(
                        variant(s3Url("uploads/notice-chat/messages/test/one.webp")),
                        variant(s3Url("uploads/notice-chat/messages/test/two.webp"))
                ));
        when(noticeChatRoomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(noticeChatMessageRepository.save(any(NoticeChatMessage.class))).thenReturn(savedMessage);

        var response = noticeChatService.sendImages(roomId.toString(), null, " 이미지와 함께 보낸 글 ", List.of(first, second));

        assertThat(response.messageType()).isEqualTo("IMAGE");
        assertThat(response.message()).isEqualTo("이미지와 함께 보낸 글");
        verify(s3ImageStorageService).storeImageVariants(eq("notice-chat"), eq("messages"), eq(currentUser.getId()), anyList());
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
        when(s3ImageStorageService.storeImageVariants(eq("notice-chat"), eq("messages"), eq(currentUser.getId()), anyList()))
                .thenReturn(List.of(variant(s3Url("uploads/notice-chat/messages/test/one.webp"))));
        when(noticeChatRoomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(noticeChatMessageRepository.save(any(NoticeChatMessage.class))).thenReturn(savedMessage);

        var response = noticeChatService.sendImages(roomId.toString(), null, "   ", List.of(file));

        assertThat(response.messageType()).isEqualTo("IMAGE");
        assertThat(response.message()).isNull();
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
        verify(s3ImageStorageService, never()).storeImageVariants(any(), any(), any(), any());
    }

    @Test
    void sendImages_rejectsTotalMediaSizeOverThirtyMegabytes() {
        UUID roomId = UUID.randomUUID();
        NoticeChatRoom room = room(roomId, openNotice, author, currentUser);
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(30L * 1024L * 1024L + 1L);

        when(userRepository.findByFirebaseUid(currentUser.getFirebaseUid())).thenReturn(Optional.of(currentUser));
        when(noticeChatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> noticeChatService.sendImages(roomId.toString(), null, null, List.of(file)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("TOTAL_MEDIA_SIZE_EXCEEDED"));
        verify(s3ImageStorageService, never()).storeImageVariants(any(), any(), any(), any());
    }

    @Test
    void sendImages_savesMp4AsVideoMessage() {
        UUID roomId = UUID.randomUUID();
        NoticeChatRoom room = room(roomId, openNotice, author, currentUser);
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(1024L);
        when(s3ImageStorageService.isVideoFile(file)).thenReturn(true);
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
        when(s3ImageStorageService.storeImageVariants(eq("notice-chat"), eq("messages"), eq(currentUser.getId()), anyList()))
                .thenReturn(List.of(new StoredImageVariant(
                        s3Url("uploads/notice-chat/messages/test/video.mp4"),
                        s3Url("uploads/notice-chat/messages/test/video.mp4"),
                        null,
                        null,
                        null
                )));
        when(noticeChatRoomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(noticeChatMessageRepository.save(any(NoticeChatMessage.class))).thenReturn(savedMessage);

        var response = noticeChatService.sendImages(roomId.toString(), null, null, List.of(file));

        assertThat(response.messageType()).isEqualTo("VIDEO");
        assertThat(room.getLastMessagePreview()).isEqualTo("동영상을 보냈습니다");
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
        when(noticeChatMessageRepository.save(any(NoticeChatMessage.class))).thenReturn(savedMessage);

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
        when(noticeChatMessageRepository.countUnreadByWatermark(room, currentUser, 0L)).thenReturn(0L);
        when(noticeChatMessageRepository.findTopByRoomAndDeletedAtIsNullOrderByRoomSequenceDescCreatedAtDesc(room)).thenReturn(Optional.empty());

        var response = noticeChatService.getRoom(roomId.toString());

        assertThat(response.lastMessageAt()).isNull();
        assertThat(response.lastMessagePreview()).isNull();
        assertThat(response.lastMessageType()).isNull();
        assertThat(response.unreadCount()).isZero();
    }

    @Test
    void getRoom_usesOpponentProfileAsDisplayThumbnailWhenNoCustomAndNoNoticeImage() {
        UUID roomId = UUID.randomUUID();
        NoticeChatRoom room = room(roomId, openNotice, author, currentUser);
        openNotice.setImages(List.of());
        author.setProfileImageUrl("https://cdn.example.com/profile/opponent.webp");

        when(userRepository.findByFirebaseUid(currentUser.getFirebaseUid())).thenReturn(Optional.of(currentUser));
        when(noticeChatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(noticeChatMessageRepository.countUnreadByWatermark(room, currentUser, 0L)).thenReturn(0L);
        when(noticeChatMessageRepository.findTopByRoomAndDeletedAtIsNullOrderByRoomSequenceDescCreatedAtDesc(room)).thenReturn(Optional.empty());

        var response = noticeChatService.getRoom(roomId.toString());

        assertThat(response.opponentProfileImageUrl()).isEqualTo("https://cdn.example.com/profile/opponent.webp");
        assertThat(response.displayThumbnailUrl()).isEqualTo("https://cdn.example.com/profile/opponent.webp");
    }

    @Test
    void getMessages_updatesReadWatermarkWithoutBulkUpdate() {
        UUID roomId = UUID.randomUUID();
        NoticeChatRoom room = room(roomId, openNotice, author, currentUser);
        NoticeChatMessage unread = NoticeChatMessage.builder()
                .id(UUID.randomUUID())
                .room(room)
                .senderUser(author)
                .messageType(NoticeChatMessageType.TEXT)
                .message("읽지 않은 메시지")
                .isRead(false)
                .roomSequence(7L)
                .createdAt(Instant.now())
                .build();

        when(userRepository.findByFirebaseUid(currentUser.getFirebaseUid())).thenReturn(Optional.of(currentUser));
        when(noticeChatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(noticeChatMessageRepository.findVisiblePage(eq(room), eq(null), eq(PageRequest.of(0, 51)))).thenReturn(List.of(unread));
        when(noticeChatReadReceiptRepository.findByRoomAndReader(room, currentUser)).thenReturn(Optional.empty());

        var response = noticeChatService.getMessages(roomId.toString(), null, null);

        assertThat(response.messages()).hasSize(1);
        assertThat(response.messages().get(0).roomSequence()).isEqualTo(7L);
        verify(participantStateRepository).save(any(NoticeChatRoomParticipantState.class));
        verify(noticeChatReadReceiptRepository).save(any());
    }

    @Test
    void getMessages_includesSenderProfileImageUrl() {
        UUID roomId = UUID.randomUUID();
        NoticeChatRoom room = room(roomId, openNotice, author, currentUser);
        author.setProfileImageUrl("https://cdn.example.com/profile/author.webp");
        NoticeChatMessage message = NoticeChatMessage.builder()
                .id(UUID.randomUUID())
                .room(room)
                .senderUser(author)
                .messageType(NoticeChatMessageType.TEXT)
                .message("프로필 이미지 포함 테스트")
                .roomSequence(1L)
                .isRead(false)
                .createdAt(Instant.now())
                .build();

        when(userRepository.findByFirebaseUid(currentUser.getFirebaseUid())).thenReturn(Optional.of(currentUser));
        when(noticeChatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(noticeChatMessageRepository.findVisiblePage(eq(room), eq(null), eq(PageRequest.of(0, 51)))).thenReturn(List.of(message));
        when(noticeChatReadReceiptRepository.findByRoomAndReader(room, currentUser)).thenReturn(Optional.empty());

        var response = noticeChatService.getMessages(roomId.toString(), null, null);

        assertThat(response.messages()).hasSize(1);
        assertThat(response.messages().get(0).senderProfileImageUrl())
                .isEqualTo("https://cdn.example.com/profile/author.webp");
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

    @Test
    void updateMessage_updatesOwnTextMessageAndBroadcasts() {
        UUID roomId = UUID.randomUUID();
        NoticeChatRoom room = room(roomId, openNotice, author, currentUser);
        NoticeChatMessage message = NoticeChatMessage.builder()
                .id(UUID.randomUUID())
                .room(room)
                .senderUser(currentUser)
                .messageType(NoticeChatMessageType.TEXT)
                .message("수정 전")
                .roomSequence(9L)
                .isRead(false)
                .createdAt(Instant.now())
                .build();
        NoticeChatMessageUpdateRequest request = new NoticeChatMessageUpdateRequest();
        request.setMessage(" 수정 후 ");

        when(userRepository.findByFirebaseUid(currentUser.getFirebaseUid())).thenReturn(Optional.of(currentUser));
        when(noticeChatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(noticeChatMessageRepository.findById(message.getId())).thenReturn(Optional.of(message));
        when(noticeChatMessageRepository.save(any(NoticeChatMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(noticeChatMessageRepository.findTopByRoomAndDeletedAtIsNullOrderByRoomSequenceDescCreatedAtDesc(room))
                .thenReturn(Optional.of(message));

        var response = noticeChatService.updateMessage(roomId.toString(), message.getId().toString(), request);

        assertThat(response.message()).isEqualTo("수정 후");
        assertThat(room.getLastMessagePreview()).isEqualTo("수정 후");
        verify(noticeChatMessageRepository).save(message);
        verify(simpMessagingTemplate).convertAndSend(eq("/topic/chat/users/" + currentUser.getId() + "/rooms/" + roomId), any(Object.class));
        verify(simpMessagingTemplate).convertAndSend(eq("/topic/chat/users/" + author.getId() + "/rooms/" + roomId), any(Object.class));
    }

    @Test
    void updateMessage_rejectsOtherUserMessage() {
        UUID roomId = UUID.randomUUID();
        NoticeChatRoom room = room(roomId, openNotice, author, currentUser);
        NoticeChatMessage message = NoticeChatMessage.builder()
                .id(UUID.randomUUID())
                .room(room)
                .senderUser(author)
                .messageType(NoticeChatMessageType.TEXT)
                .message("상대 메시지")
                .createdAt(Instant.now())
                .build();
        NoticeChatMessageUpdateRequest request = new NoticeChatMessageUpdateRequest();
        request.setMessage("수정 시도");

        when(userRepository.findByFirebaseUid(currentUser.getFirebaseUid())).thenReturn(Optional.of(currentUser));
        when(noticeChatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(noticeChatMessageRepository.findById(message.getId())).thenReturn(Optional.of(message));

        assertThatThrownBy(() -> noticeChatService.updateMessage(roomId.toString(), message.getId().toString(), request))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("CHAT_MESSAGE_FORBIDDEN"));
    }

    @Test
    void deleteMessage_softDeletesOwnMessageAndClearsEmptyRoomPreview() {
        UUID roomId = UUID.randomUUID();
        NoticeChatRoom room = room(roomId, openNotice, author, currentUser);
        NoticeChatMessage message = NoticeChatMessage.builder()
                .id(UUID.randomUUID())
                .room(room)
                .senderUser(currentUser)
                .messageType(NoticeChatMessageType.TEXT)
                .message("삭제할 메시지")
                .roomSequence(4L)
                .isRead(false)
                .createdAt(Instant.now())
                .build();
        room.setLastMessagePreview("삭제할 메시지");
        room.setLastMessageType(NoticeChatMessageType.TEXT);
        room.setLastMessageAt(message.getCreatedAt());

        when(userRepository.findByFirebaseUid(currentUser.getFirebaseUid())).thenReturn(Optional.of(currentUser));
        when(noticeChatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(noticeChatMessageRepository.findById(message.getId())).thenReturn(Optional.of(message));
        when(noticeChatMessageRepository.saveAndFlush(any(NoticeChatMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(noticeChatMessageRepository.findTopByRoomAndDeletedAtIsNullOrderByRoomSequenceDescCreatedAtDesc(room))
                .thenReturn(Optional.empty());

        noticeChatService.deleteMessage(roomId.toString(), message.getId().toString());

        assertThat(message.getDeletedAt()).isNotNull();
        assertThat(room.getLastMessagePreview()).isNull();
        assertThat(room.getLastMessageType()).isNull();
        assertThat(room.getLastMessageAt()).isNull();
        verify(noticeChatRoomRepository).save(room);
        verify(simpMessagingTemplate).convertAndSend(eq("/topic/chat/users/" + currentUser.getId() + "/rooms/" + roomId), any(Object.class));
        verify(simpMessagingTemplate).convertAndSend(eq("/topic/chat/users/" + author.getId() + "/rooms/" + roomId), any(Object.class));
    }

    @Test
    void deleteMessage_rejectsOtherUserMessage() {
        UUID roomId = UUID.randomUUID();
        NoticeChatRoom room = room(roomId, openNotice, author, currentUser);
        NoticeChatMessage message = NoticeChatMessage.builder()
                .id(UUID.randomUUID())
                .room(room)
                .senderUser(author)
                .messageType(NoticeChatMessageType.TEXT)
                .message("상대 메시지")
                .createdAt(Instant.now())
                .build();

        when(userRepository.findByFirebaseUid(currentUser.getFirebaseUid())).thenReturn(Optional.of(currentUser));
        when(noticeChatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(noticeChatMessageRepository.findById(message.getId())).thenReturn(Optional.of(message));

        assertThatThrownBy(() -> noticeChatService.deleteMessage(roomId.toString(), message.getId().toString()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("CHAT_MESSAGE_FORBIDDEN"));
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
                .build();
    }

    private StoredImageVariant variant(String webpUrl) {
        return new StoredImageVariant(webpUrl.replace(".webp", ".jpg"), webpUrl, webpUrl.replace(".webp", "-medium.webp"), webpUrl.replace(".webp", "-thumbnail.webp"), webpUrl.replace(".webp", "-preview.webp"));
    }

    private String s3Url(String objectKey) {
        return S3_BASE_URL + "/" + objectKey;
    }
}
