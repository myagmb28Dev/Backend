package com.example.pogun.service.noticechat;

import com.example.pogun.dto.common.ApiResponse.ApiException;
import com.example.pogun.dto.noticechat.NoticeChatMessageRequest;
import com.example.pogun.dto.noticechat.NoticeChatMessageImageResponse;
import com.example.pogun.dto.noticechat.NoticeChatMessageReplyResponse;
import com.example.pogun.dto.noticechat.NoticeChatMessageResponse;
import com.example.pogun.dto.noticechat.NoticeChatImageOriginalResponse;
import com.example.pogun.dto.noticechat.NoticeChatRoomEventRequest;
import com.example.pogun.dto.noticechat.NoticeChatRoomCreateResult;
import com.example.pogun.dto.noticechat.NoticeChatRoomResponse;
import com.example.pogun.dto.noticechat.NoticeChatRoomSettingsRequest;
import com.example.pogun.dto.noticechat.NoticeChatTypingRequest;
import com.example.pogun.dto.storage.StoredImageVariant;
import com.example.pogun.entity.missingpet.PetNotice;
import com.example.pogun.entity.missingpet.enums.PetNoticeStatus;
import com.example.pogun.entity.noticechat.NoticeChatMessage;
import com.example.pogun.entity.noticechat.NoticeChatMessageImage;
import com.example.pogun.entity.noticechat.NoticeChatRoom;
import com.example.pogun.entity.noticechat.NoticeChatRoomParticipantState;
import com.example.pogun.entity.noticechat.enums.NoticeChatMessageType;
import com.example.pogun.entity.noticechat.enums.NoticeChatRoomStatus;
import com.example.pogun.entity.user.User;
import com.example.pogun.entity.user.enums.UserStatus;
import com.example.pogun.repository.missingpet.PetNoticeRepository;
import com.example.pogun.repository.noticechat.NoticeChatMessageRepository;
import com.example.pogun.repository.noticechat.NoticeChatMessageImageRepository;
import com.example.pogun.repository.noticechat.NoticeChatRoomParticipantStateRepository;
import com.example.pogun.repository.noticechat.NoticeChatRoomRepository;
import com.example.pogun.repository.user.UserBlockRepository;
import com.example.pogun.repository.user.UserRepository;
import com.example.pogun.service.storage.LocalImageStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.time.Instant;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class NoticeChatService {
    private static final int MAX_IMAGE_COUNT = 10;
    private static final String IMAGE_MESSAGE_PREVIEW = "사진을 보냈습니다";

    private final NoticeChatRoomRepository noticeChatRoomRepository;
    private final NoticeChatMessageRepository noticeChatMessageRepository;
    private final NoticeChatMessageImageRepository noticeChatMessageImageRepository;
    private final NoticeChatRoomParticipantStateRepository participantStateRepository;
    private final PetNoticeRepository petNoticeRepository;
    private final UserRepository userRepository;
    private final UserBlockRepository userBlockRepository;
    private final SimpMessagingTemplate simpMessagingTemplate;
    private final LocalImageStorageService localImageStorageService;

    @Transactional
    public NoticeChatRoomCreateResult createOrGetRoom(String noticeId) {
        User currentUser = getCurrentUser();
        assertChatAvailableUser(currentUser, "채팅방을 생성할 수 없습니다.");

        PetNotice notice = getNoticeById(noticeId);
        assertNoticeAvailableForNewChat(notice, currentUser);

        User owner = notice.getAuthor();
        assertOpponentCanReceiveChat(owner);
        assertNotBlockedEitherDirection(currentUser, owner);

        return noticeChatRoomRepository.findByNoticeAndOwnerUserAndGuestUser(notice, owner, currentUser)
                .map(room -> {
                    ensureParticipantStates(room);
                    NoticeChatRoomParticipantState currentState = ensureParticipantState(room, currentUser);
                    currentState.setLeftAt(null);
                    participantStateRepository.save(currentState);
                    RoomUpdatePayload roomUpdatePayload = buildRoomUpdatePayload(room);
                    afterCommitOrNow(() -> broadcastRoomUpdate(roomUpdatePayload));
                    return new NoticeChatRoomCreateResult(false, toRoomResponse(room, currentUser));
                })
                .orElseGet(() -> {
                    NoticeChatRoom saved = noticeChatRoomRepository.save(NoticeChatRoom.builder()
                            .notice(notice)
                            .ownerUser(owner)
                            .guestUser(currentUser)
                            .status(NoticeChatRoomStatus.OPEN)
                            .build());
                    ensureParticipantStates(saved);
                    RoomUpdatePayload roomUpdatePayload = buildRoomUpdatePayload(saved);
                    afterCommitOrNow(() -> broadcastRoomUpdate(roomUpdatePayload));
                    return new NoticeChatRoomCreateResult(true, toRoomResponse(saved, currentUser));
                });
    }

    @Transactional
    public List<NoticeChatRoomResponse> getRooms() {
        User currentUser = getCurrentUser();
        assertChatAvailableUser(currentUser, "채팅방 목록을 조회할 수 없습니다.");
        return noticeChatRoomRepository.findVisibleRoomsForUser(currentUser.getId()).stream()
                .filter(room -> ensureParticipantState(room, currentUser).getLeftAt() == null)
                .sorted(roomComparator(currentUser))
                .map(room -> toRoomResponse(room, currentUser))
                .toList();
    }

    @Transactional
    public List<NoticeChatRoomResponse> searchRooms(String keyword) {
        String normalizedKeyword = trimToNull(keyword);
        if (normalizedKeyword == null) {
            return getRooms();
        }
        String lowerKeyword = normalizedKeyword.toLowerCase();
        return getRooms().stream()
                .filter(room -> containsIgnoreCase(room.noticeTitle(), lowerKeyword)
                        || containsIgnoreCase(room.opponentNickname(), lowerKeyword))
                .toList();
    }

    @Transactional
    public NoticeChatRoomResponse getRoom(String roomId) {
        User currentUser = getCurrentUser();
        assertChatAvailableUser(currentUser, "채팅방 상세를 조회할 수 없습니다.");
        return toRoomResponse(getAccessibleRoom(roomId, currentUser), currentUser);
    }

    @Transactional
    public List<NoticeChatMessageResponse> getMessages(String roomId) {
        User currentUser = getCurrentUser();
        assertChatAvailableUser(currentUser, "채팅 메시지를 조회할 수 없습니다.");
        NoticeChatRoom room = getAccessibleRoom(roomId, currentUser);
        log.info("채팅 메시지 조회 시작 roomId={} userId={}", room.getId(), currentUser.getId());

        List<NoticeChatMessage> messages = noticeChatMessageRepository.findByRoomOrderByRoomSequenceAscCreatedAtAsc(room);
        markRoomAsRead(room, currentUser, messages);

        log.info("채팅 메시지 조회 완료 roomId={} messageCount={} userId={}", room.getId(), messages.size(), currentUser.getId());
        return messages.stream().map(message -> toMessageResponse(message, currentUser)).toList();
    }

    @Transactional
    public NoticeChatRoomResponse markRoomAsRead(String roomId) {
        User currentUser = getCurrentUser();
        assertChatAvailableUser(currentUser, "채팅방 읽음 처리를 할 수 없습니다.");
        NoticeChatRoom room = getAccessibleRoom(roomId, currentUser);
        List<NoticeChatMessage> messages = noticeChatMessageRepository.findByRoomOrderByRoomSequenceAscCreatedAtAsc(room);
        markRoomAsRead(room, currentUser, messages);
        return toRoomResponse(room, currentUser);
    }

    @Transactional(readOnly = true)
    public List<NoticeChatMessageResponse> searchMessages(String roomId, String keyword) {
        User currentUser = getCurrentUser();
        assertChatAvailableUser(currentUser, "채팅 메시지를 검색할 수 없습니다.");
        NoticeChatRoom room = getAccessibleRoom(roomId, currentUser);
        String normalizedKeyword = trimToNull(keyword);
        if (normalizedKeyword == null) {
            return List.of();
        }
        return noticeChatMessageRepository.findByRoomAndMessageContainingIgnoreCaseOrderByRoomSequenceAscCreatedAtAsc(room, normalizedKeyword).stream()
                .filter(message -> resolveMessageType(message) == NoticeChatMessageType.TEXT)
                .map(message -> toMessageResponse(message, currentUser))
                .toList();
    }

    @Transactional
    public NoticeChatImageOriginalResponse getOriginalImage(String imageId) {
        User currentUser = getCurrentUser();
        assertChatAvailableUser(currentUser, "원본 이미지를 조회할 수 없습니다.");
        NoticeChatMessageImage image = getMessageImage(imageId);
        getAccessibleRoom(image.getMessage().getRoom().getId().toString(), currentUser);
        String originalUrl = image.getOriginalUrl() != null ? image.getOriginalUrl() : image.getImageUrl();
        return new NoticeChatImageOriginalResponse(image.getId(), originalUrl);
    }

    @Transactional
    public NoticeChatMessageResponse sendMessage(Principal principal, NoticeChatMessageRequest request) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw ApiException.unauthorized("WEBSOCKET_UNAUTHORIZED", "웹소켓 사용자 인증 정보를 찾을 수 없습니다.");
        }

        User sender = getUserByFirebaseUid(principal.getName());
        assertChatAvailableUser(sender, "메시지를 전송할 수 없습니다.");
        NoticeChatRoom room = getAccessibleRoom(request.getRoomId().toString(), sender);
        assertRoomAcceptsConversation(room);
        User opponent = getOpponent(room, sender);
        assertOpponentCanReceiveChat(opponent);
        assertNotBlockedEitherDirection(sender, opponent);
        String clientMessageId = trimToNull(request.getClientMessageId());
        if (clientMessageId != null) {
            NoticeChatMessage existing = noticeChatMessageRepository.findByRoomAndSenderUserAndClientMessageId(room, sender, clientMessageId)
                    .orElse(null);
            if (existing != null) {
                return toMessageResponse(existing, sender);
            }
        }
        String content = trimToNull(request.getMessage());
        log.info("채팅 메시지 전송 시작 roomId={} senderUserId={} messageLength={}",
                room.getId(), sender.getId(), content == null ? 0 : content.length());

        if (room.getStatus() == NoticeChatRoomStatus.CLOSED) {
            throw ApiException.conflict("CHAT_ROOM_CLOSED", "종료된 채팅방입니다.");
        }
        if (content == null) {
            throw ApiException.badRequest("EMPTY_MESSAGE", "메시지 내용은 비어 있을 수 없습니다.");
        }
        NoticeChatMessage replyToMessage = resolveReplyTarget(request.getReplyToMessageId(), room);

        NoticeChatMessage saved = saveMessage(
                room,
                sender,
                NoticeChatMessageType.TEXT,
                content,
                replyToMessage,
                clientMessageId,
                List.of()
        );
        return broadcastMessage(room, sender, saved);
    }

    @Transactional
    public NoticeChatMessageResponse sendImages(String roomId, String replyToMessageId, String message, List<MultipartFile> images) {
        User sender = getCurrentUser();
        assertChatAvailableUser(sender, "이미지 메시지를 전송할 수 없습니다.");
        NoticeChatRoom room = getAccessibleRoom(roomId, sender);
        assertRoomAcceptsConversation(room);
        User opponent = getOpponent(room, sender);
        assertOpponentCanReceiveChat(opponent);
        assertNotBlockedEitherDirection(sender, opponent);

        List<MultipartFile> nonEmptyImages = images == null ? List.of() : images.stream()
                .filter(file -> file != null && !file.isEmpty())
                .toList();
        if (nonEmptyImages.isEmpty()) {
            throw ApiException.badRequest("EMPTY_IMAGE_MESSAGE", "이미지는 최소 1장 이상 첨부해야 합니다.");
        }
        if (nonEmptyImages.size() > MAX_IMAGE_COUNT) {
            throw ApiException.badRequest("TOO_MANY_IMAGES", "이미지는 최대 10장까지 첨부할 수 있습니다.");
        }
        String content = trimToNull(message);
        if (content != null && content.length() > 2000) {
            throw ApiException.badRequest("MESSAGE_TOO_LONG", "message는 2000자를 초과할 수 없습니다.");
        }

        NoticeChatMessage replyToMessage = resolveReplyTarget(parseNullableUuid(replyToMessageId, "INVALID_REPLY_MESSAGE_ID", "올바르지 않은 답장 메시지 ID 형식입니다."), room);
        List<StoredImageVariant> imageVariants = localImageStorageService.storeImageVariants("notice-chat", "messages", sender.getId(), nonEmptyImages);
        NoticeChatMessage saved = saveMessage(
                room,
                sender,
                NoticeChatMessageType.IMAGE,
                content,
                replyToMessage,
                null,
                imageVariants
        );
        return broadcastMessage(room, sender, saved);
    }

    @Transactional
    public NoticeChatRoomResponse enterRoom(Principal principal, NoticeChatRoomEventRequest request) {
        User user = getEventUser(principal, "채팅방에 입장할 수 없습니다.");
        NoticeChatRoom room = getAccessibleRoom(request.getRoomId().toString(), user);
        NoticeChatRoomParticipantState state = ensureParticipantState(room, user);
        state.setOnline(true);
        state.setLastActiveAt(Instant.now());
        participantStateRepository.save(state);
        markRoomAsRead(room, user, noticeChatMessageRepository.findByRoomOrderByRoomSequenceAscCreatedAtAsc(room));
        sendRoomLifecycleEvent(room, user, "ROOM_ENTERED");
        return toRoomResponse(room, user);
    }

    @Transactional
    public NoticeChatRoomResponse leaveSocketRoom(Principal principal, NoticeChatRoomEventRequest request) {
        User user = getEventUser(principal, "채팅방에서 퇴장할 수 없습니다.");
        NoticeChatRoom room = getAccessibleRoom(request.getRoomId().toString(), user);
        NoticeChatRoomParticipantState state = ensureParticipantState(room, user);
        state.setOnline(false);
        state.setLastActiveAt(Instant.now());
        participantStateRepository.save(state);
        sendRoomLifecycleEvent(room, user, "ROOM_LEFT");
        return toRoomResponse(room, user);
    }

    @Transactional
    public NoticeChatRoomResponse leaveRoom(String roomId) {
        User currentUser = getCurrentUser();
        assertChatAvailableUser(currentUser, "채팅방을 나갈 수 없습니다.");
        NoticeChatRoom room = getAccessibleRoom(roomId, currentUser);
        NoticeChatRoomParticipantState state = ensureParticipantState(room, currentUser);
        state.setLeftAt(Instant.now());
        state.setOnline(false);
        state.setLastActiveAt(state.getLeftAt());
        participantStateRepository.save(state);
        sendRoomLifecycleEvent(room, currentUser, "ROOM_LEFT");
        return toRoomResponse(room, currentUser);
    }

    @Transactional
    public NoticeChatRoomResponse updateSettings(String roomId, NoticeChatRoomSettingsRequest request) {
        User currentUser = getCurrentUser();
        assertChatAvailableUser(currentUser, "채팅방 설정을 변경할 수 없습니다.");
        NoticeChatRoom room = getAccessibleRoom(roomId, currentUser);
        NoticeChatRoomParticipantState state = ensureParticipantState(room, currentUser);
        if (request.getNotificationEnabled() != null) {
            state.setNotificationEnabled(request.getNotificationEnabled());
        }
        if (request.getFavorite() != null) {
            state.setFavorite(request.getFavorite());
        }
        if (request.getPinned() != null) {
            state.setPinned(request.getPinned());
        }
        participantStateRepository.save(state);
        return toRoomResponse(room, currentUser);
    }

    @Transactional
    public void markUserOffline(String firebaseUid) {
        if (firebaseUid == null || firebaseUid.isBlank()) {
            return;
        }
        userRepository.findByFirebaseUid(firebaseUid).ifPresent(user -> {
            Instant now = Instant.now();
            for (NoticeChatRoomParticipantState state : participantStateRepository.findByUser(user)) {
                state.setOnline(false);
                state.setLastActiveAt(now);
            }
        });
    }

    @Transactional(readOnly = true)
    public Map<String, Object> sendTypingEvent(Principal principal, NoticeChatTypingRequest request) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw ApiException.unauthorized("WEBSOCKET_UNAUTHORIZED", "웹소켓 사용자 인증 정보를 찾을 수 없습니다.");
        }
        if (request.getRoomId() == null) {
            throw ApiException.badRequest("MISSING_ROOM_ID", "채팅방 ID는 필수입니다.");
        }

        User sender = getUserByFirebaseUid(principal.getName());
        assertChatAvailableUser(sender, "입력 중 상태를 전송할 수 없습니다.");
        NoticeChatRoom room = getAccessibleRoom(request.getRoomId().toString(), sender);
        assertRoomAcceptsConversation(room);
        User opponent = getOpponent(room, sender);
        assertOpponentCanReceiveChat(opponent);
        assertNotBlockedEitherDirection(sender, opponent);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("roomId", room.getId());
        payload.put("senderUserId", sender.getId());
        payload.put("senderNickname", sender.getNickname());
        payload.put("isTyping", Boolean.TRUE.equals(request.getTyping()));

        simpMessagingTemplate.convertAndSend(
                userTypingTopic(opponent.getId(), room.getId()),
                (Object) payload
        );
        return payload;
    }

    private User getCurrentUser() {
        String firebaseUid = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return getUserByFirebaseUid(firebaseUid);
    }

    private User getUserByFirebaseUid(String firebaseUid) {
        return userRepository.findByFirebaseUid(firebaseUid)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));
    }

    private User getEventUser(Principal principal, String fallbackMessage) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw ApiException.unauthorized("WEBSOCKET_UNAUTHORIZED", "웹소켓 사용자 인증 정보를 찾을 수 없습니다.");
        }
        User user = getUserByFirebaseUid(principal.getName());
        assertChatAvailableUser(user, fallbackMessage);
        return user;
    }

    private PetNotice getNoticeById(String noticeId) {
        try {
            UUID parsedId = UUID.fromString(noticeId);
            return petNoticeRepository.findById(parsedId)
                    .orElseThrow(() -> ApiException.notFound("NOTICE_NOT_FOUND", "실종 공고를 찾을 수 없습니다."));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("INVALID_NOTICE_ID", "올바르지 않은 공고 ID 형식입니다.");
        }
    }

    private NoticeChatRoom getAccessibleRoom(String roomId, User currentUser) {
        NoticeChatRoom room;
        try {
            room = noticeChatRoomRepository.findById(UUID.fromString(roomId))
                    .orElseThrow(() -> ApiException.notFound("CHAT_ROOM_NOT_FOUND", "채팅방을 찾을 수 없습니다."));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("INVALID_CHAT_ROOM_ID", "올바르지 않은 채팅방 ID 형식입니다.");
        }

        boolean participant = room.getOwnerUser().getId().equals(currentUser.getId())
                || room.getGuestUser().getId().equals(currentUser.getId());
        if (!participant) {
            throw ApiException.forbidden("CHAT_ROOM_FORBIDDEN", "해당 채팅방에 접근할 수 없습니다.");
        }
        if (room.getNotice() == null) {
            throw ApiException.notFound("CHAT_ROOM_NOT_FOUND", "유효하지 않은 채팅방입니다.");
        }
        return room;
    }

    private NoticeChatRoomResponse toRoomResponse(NoticeChatRoom room, User currentUser) {
        User opponent = getOpponent(room, currentUser);
        NoticeChatMessageType lastMessageType = resolveLastMessageType(room);
        NoticeChatRoomParticipantState currentState = ensureParticipantState(room, currentUser);
        NoticeChatRoomParticipantState opponentState = ensureParticipantState(room, opponent);
        return new NoticeChatRoomResponse(
                room.getId(),
                room.getNotice().getId(),
                room.getNotice().getTitle(),
                room.getStatus().name(),
                lastMessageType != null ? lastMessageType.name() : null,
                room.getLastMessageAt(),
                resolveLastMessagePreview(room, lastMessageType),
                room.getCreatedAt(),
                opponent.getId(),
                opponent.getNickname(),
                noticeChatMessageRepository.countByRoomAndSenderUserNotAndIsReadFalse(room, currentUser),
                currentState.getNotificationEnabled(),
                currentState.getFavorite(),
                currentState.getPinned(),
                currentState.getLeftAt(),
                opponentState.getOnline(),
                opponentState.getLastActiveAt(),
                currentState.getLastReadMessage() != null ? currentState.getLastReadMessage().getId() : null,
                currentState.getLastReadAt()
        );
    }

    private NoticeChatMessageResponse toMessageResponse(NoticeChatMessage message, User currentUser) {
        NoticeChatMessageType messageType = resolveMessageType(message);
        return new NoticeChatMessageResponse(
                message.getId(),
                message.getRoom().getId(),
                message.getSenderUser().getId(),
                message.getSenderUser().getNickname(),
                message.getMessage(),
                messageType.name(),
                message.getImages().stream()
                        .map(image -> new NoticeChatMessageImageResponse(
                                image.getId(),
                                image.getImageUrl(),
                                image.getWebpUrl() != null ? image.getWebpUrl() : image.getImageUrl(),
                                image.getMediumUrl(),
                                image.getThumbnailUrl(),
                                image.getPreviewUrl(),
                                image.getDisplayOrder()
                        ))
                        .toList(),
                toReplyResponse(message.getReplyToMessage()),
                message.getIsRead(),
                message.getSenderUser().getId().equals(currentUser.getId()),
                message.getCreatedAt(),
                message.getClientMessageId(),
                message.getRoomSequence(),
                message.getCreatedAt()
        );
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void assertChatAvailableUser(User user, String fallbackMessage) {
        if (user.getStatus() == null || user.getStatus() == UserStatus.ACTIVE) {
            return;
        }
        throw switch (user.getStatus()) {
            case BANNED -> ApiException.forbidden("CHAT_USER_BANNED", "제재된 사용자는 채팅을 이용할 수 없습니다.");
            case WITHDRAWN -> ApiException.forbidden("CHAT_USER_WITHDRAWN", "탈퇴한 사용자는 채팅을 이용할 수 없습니다.");
            case ACTIVE -> ApiException.forbidden("CHAT_USER_INACTIVE", fallbackMessage);
        };
    }

    private void assertOpponentCanReceiveChat(User opponent) {
        if (opponent.getStatus() == null || opponent.getStatus() == UserStatus.ACTIVE) {
            return;
        }
        throw switch (opponent.getStatus()) {
            case BANNED -> ApiException.conflict("CHAT_OPPONENT_BANNED", "상대방이 채팅을 받을 수 없는 상태입니다.");
            case WITHDRAWN -> ApiException.conflict("CHAT_OPPONENT_WITHDRAWN", "탈퇴한 사용자와는 채팅할 수 없습니다.");
            case ACTIVE -> ApiException.conflict("CHAT_OPPONENT_UNAVAILABLE", "상대방이 채팅을 받을 수 없는 상태입니다.");
        };
    }

    private void assertNotBlockedEitherDirection(User sender, User opponent) {
        if (userBlockRepository.existsByBlockerAndBlocked(sender, opponent)
                || userBlockRepository.existsByBlockerAndBlocked(opponent, sender)) {
            throw ApiException.forbidden("CHAT_USER_BLOCKED", "차단된 사용자와는 채팅할 수 없습니다.");
        }
    }

    private void assertNoticeAvailableForNewChat(PetNotice notice, User currentUser) {
        if (Boolean.TRUE.equals(notice.getHidden())) {
            throw ApiException.conflict("CHAT_NOTICE_HIDDEN", "숨김 처리된 공고로는 채팅을 시작할 수 없습니다.");
        }
        if (notice.getStatus() != PetNoticeStatus.OPEN) {
            throw ApiException.conflict("CHAT_NOTICE_CLOSED", "종료된 공고로는 새 채팅을 시작할 수 없습니다.");
        }
        if (notice.getAuthor().getId().equals(currentUser.getId())) {
            throw ApiException.conflict("CHAT_ROOM_SELF_NOTICE", "본인 공고에는 직접 문의할 수 없습니다.");
        }
    }

    private void assertRoomAcceptsConversation(NoticeChatRoom room) {
        PetNotice notice = room.getNotice();
        if (Boolean.TRUE.equals(notice.getHidden())) {
            throw ApiException.conflict("CHAT_NOTICE_HIDDEN", "숨김 처리된 공고의 채팅방입니다.");
        }
        if (notice.getStatus() != PetNoticeStatus.OPEN) {
            throw ApiException.conflict("CHAT_NOTICE_CLOSED", "종료된 공고의 채팅방입니다.");
        }
    }

    private User getOpponent(NoticeChatRoom room, User currentUser) {
        return room.getOwnerUser().getId().equals(currentUser.getId()) ? room.getGuestUser() : room.getOwnerUser();
    }

    private String resolveLastMessagePreview(NoticeChatRoom room, NoticeChatMessageType lastMessageType) {
        if (room.getLastMessagePreview() != null && !room.getLastMessagePreview().isBlank()) {
            return room.getLastMessagePreview();
        }
        if (lastMessageType == NoticeChatMessageType.IMAGE) {
            return IMAGE_MESSAGE_PREVIEW;
        }
        return noticeChatMessageRepository.findTopByRoomOrderByCreatedAtDesc(room)
                .map(this::toPreview)
                .orElse(null);
    }

    private NoticeChatMessageType resolveLastMessageType(NoticeChatRoom room) {
        if (room.getLastMessageType() != null) {
            return room.getLastMessageType();
        }
        return noticeChatMessageRepository.findTopByRoomOrderByCreatedAtDesc(room)
                .map(this::resolveMessageType)
                .orElse(null);
    }

    private String userRoomTopic(UUID userId, UUID roomId) {
        return "/topic/chat/users/" + userId + "/rooms/" + roomId;
    }

    private String userRoomsTopic(UUID userId) {
        return "/topic/chat/users/" + userId + "/rooms";
    }

    private String userTypingTopic(UUID userId, UUID roomId) {
        return userRoomTopic(userId, roomId) + "/typing";
    }

    private void sendReadReceipt(
            UUID roomId,
            UUID readerUserId,
            UUID opponentUserId,
            UUID latestReadMessageId,
            int readCount
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "READ_RECEIPT");
        payload.put("roomId", roomId);
        payload.put("readerUserId", readerUserId);
        payload.put("latestReadMessageId", latestReadMessageId);
        payload.put("readCount", readCount);
        simpMessagingTemplate.convertAndSend(userRoomTopic(opponentUserId, roomId), (Object) payload);
    }

    private void sendRoomLifecycleEvent(NoticeChatRoom room, User user, String type) {
        User opponent = getOpponent(room, user);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", type);
        payload.put("roomId", room.getId());
        payload.put("userId", user.getId());
        payload.put("nickname", user.getNickname());
        payload.put("lastActiveAt", Instant.now());
        simpMessagingTemplate.convertAndSend(userRoomTopic(opponent.getId(), room.getId()), (Object) payload);
    }

    private RoomUpdatePayload buildRoomUpdatePayload(NoticeChatRoom room) {
        return new RoomUpdatePayload(
                room.getOwnerUser().getId(),
                toRoomResponse(room, room.getOwnerUser()),
                room.getGuestUser().getId(),
                toRoomResponse(room, room.getGuestUser())
        );
    }

    private void broadcastRoomUpdate(RoomUpdatePayload roomUpdatePayload) {
        simpMessagingTemplate.convertAndSend(userRoomsTopic(roomUpdatePayload.ownerUserId()), roomUpdatePayload.ownerPayload());
        simpMessagingTemplate.convertAndSend(userRoomsTopic(roomUpdatePayload.guestUserId()), roomUpdatePayload.guestPayload());
    }

    private NoticeChatMessage saveMessage(
            NoticeChatRoom room,
            User sender,
            NoticeChatMessageType messageType,
            String content,
            NoticeChatMessage replyToMessage,
            String clientMessageId,
            List<StoredImageVariant> imageVariants
    ) {
        List<NoticeChatMessageImage> images = new ArrayList<>();
        NoticeChatMessage message = NoticeChatMessage.builder()
                .room(room)
                .senderUser(sender)
                .messageType(messageType)
                .message(content)
                .replyToMessage(replyToMessage)
                .clientMessageId(clientMessageId)
                .roomSequence(noticeChatMessageRepository.findMaxRoomSequence(room) + 1)
                .images(images)
                .build();
        for (int index = 0; index < imageVariants.size(); index++) {
            StoredImageVariant variant = imageVariants.get(index);
            images.add(NoticeChatMessageImage.builder()
                    .message(message)
                    .imageUrl(variant.webpUrl())
                    .originalUrl(variant.originalUrl())
                    .webpUrl(variant.webpUrl())
                    .mediumUrl(variant.mediumUrl())
                    .thumbnailUrl(variant.thumbnailUrl())
                    .previewUrl(variant.previewUrl())
                    .displayOrder(index)
                    .build());
        }

        NoticeChatMessage saved = noticeChatMessageRepository.saveAndFlush(message);
        room.setLastMessageAt(saved.getCreatedAt() != null ? saved.getCreatedAt() : Instant.now());
        room.setLastMessageType(saved.getMessageType());
        room.setLastMessagePreview(toPreview(saved));
        noticeChatRoomRepository.save(room);
        log.info("채팅 메시지 저장 완료 roomId={} messageId={} senderUserId={} type={} createdAt={}",
                room.getId(), saved.getId(), sender.getId(), saved.getMessageType(), saved.getCreatedAt());
        return saved;
    }

    private void markRoomAsRead(NoticeChatRoom room, User reader, List<NoticeChatMessage> messages) {
        int updatedCount = noticeChatMessageRepository.markUnreadMessagesAsRead(room, reader);
        if (updatedCount > 0) {
            messages.stream()
                    .filter(message -> !message.getSenderUser().getId().equals(reader.getId()))
                    .forEach(message -> message.setIsRead(true));
        }
        NoticeChatMessage latestReadMessage = messages.stream()
                .filter(message -> !message.getSenderUser().getId().equals(reader.getId()))
                .reduce((first, second) -> second)
                .orElse(null);
        NoticeChatRoomParticipantState state = ensureParticipantState(room, reader);
        if (latestReadMessage != null) {
            state.setLastReadMessage(latestReadMessage);
            state.setLastReadAt(Instant.now());
            participantStateRepository.save(state);
        }
        if (updatedCount > 0) {
            UUID readRoomId = room.getId();
            UUID readerUserId = reader.getId();
            UUID opponentUserId = getOpponent(room, reader).getId();
            UUID latestReadMessageId = latestReadMessage != null ? latestReadMessage.getId() : null;
            afterCommitOrNow(() -> sendReadReceipt(
                    readRoomId,
                    readerUserId,
                    opponentUserId,
                    latestReadMessageId,
                    updatedCount
            ));
        }
    }

    private void ensureParticipantStates(NoticeChatRoom room) {
        ensureParticipantState(room, room.getOwnerUser());
        ensureParticipantState(room, room.getGuestUser());
    }

    private NoticeChatRoomParticipantState ensureParticipantState(NoticeChatRoom room, User user) {
        return participantStateRepository.findByRoomAndUser(room, user)
                .orElseGet(() -> participantStateRepository.save(NoticeChatRoomParticipantState.builder()
                        .room(room)
                        .user(user)
                        .notificationEnabled(true)
                        .favorite(false)
                        .pinned(false)
                        .online(false)
                        .build()));
    }

    private Comparator<NoticeChatRoom> roomComparator(User currentUser) {
        return Comparator
                .comparing((NoticeChatRoom room) -> Boolean.TRUE.equals(ensureParticipantState(room, currentUser).getPinned())).reversed()
                .thenComparing(room -> room.getLastMessageAt() != null ? room.getLastMessageAt() : room.getCreatedAt(), Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(NoticeChatRoom::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private boolean containsIgnoreCase(String value, String lowerKeyword) {
        return value != null && value.toLowerCase().contains(lowerKeyword);
    }

    private NoticeChatMessageResponse broadcastMessage(NoticeChatRoom room, User sender, NoticeChatMessage saved) {
        User opponent = getOpponent(room, sender);
        NoticeChatMessageResponse senderPayload = toMessageResponse(saved, sender);
        NoticeChatMessageResponse opponentPayload = toMessageResponse(saved, opponent);
        RoomUpdatePayload roomUpdatePayload = buildRoomUpdatePayload(room);

        afterCommitOrNow(() -> {
            simpMessagingTemplate.convertAndSend(userRoomTopic(sender.getId(), room.getId()), senderPayload);
            simpMessagingTemplate.convertAndSend(userRoomTopic(opponent.getId(), room.getId()), opponentPayload);
            broadcastRoomUpdate(roomUpdatePayload);
        });
        return senderPayload;
    }

    private String toPreview(NoticeChatMessage message) {
        if (message == null) {
            return null;
        }
        if (resolveMessageType(message) == NoticeChatMessageType.IMAGE) {
            return IMAGE_MESSAGE_PREVIEW;
        }
        String trimmed = trimToNull(message.getMessage());
        if (trimmed == null) {
            return null;
        }
        return trimmed.length() <= 80 ? trimmed : trimmed.substring(0, 77) + "...";
    }

    private NoticeChatMessageReplyResponse toReplyResponse(NoticeChatMessage replyToMessage) {
        if (replyToMessage == null) {
            return null;
        }
        String preview = resolveMessageType(replyToMessage) == NoticeChatMessageType.IMAGE
                ? "사진"
                : toPreview(replyToMessage);
        return new NoticeChatMessageReplyResponse(
                replyToMessage.getId(),
                replyToMessage.getSenderUser().getId(),
                replyToMessage.getSenderUser().getNickname(),
                preview
        );
    }

    private NoticeChatMessage resolveReplyTarget(UUID replyToMessageId, NoticeChatRoom room) {
        if (replyToMessageId == null) {
            return null;
        }
        NoticeChatMessage replyToMessage = noticeChatMessageRepository.findById(replyToMessageId)
                .orElseThrow(() -> ApiException.notFound("REPLY_MESSAGE_NOT_FOUND", "답장 대상 메시지를 찾을 수 없습니다."));
        if (!replyToMessage.getRoom().getId().equals(room.getId())) {
            throw ApiException.badRequest("INVALID_REPLY_MESSAGE", "같은 채팅방의 메시지에만 답장할 수 있습니다.");
        }
        return replyToMessage;
    }

    private NoticeChatMessageImage getMessageImage(String imageId) {
        try {
            return noticeChatMessageImageRepository.findById(UUID.fromString(imageId))
                    .orElseThrow(() -> ApiException.notFound("CHAT_IMAGE_NOT_FOUND", "채팅 이미지를 찾을 수 없습니다."));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("INVALID_CHAT_IMAGE_ID", "올바르지 않은 채팅 이미지 ID 형식입니다.");
        }
    }

    private UUID parseNullableUuid(String value, String code, String message) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest(code, message);
        }
    }

    private void afterCommitOrNow(Runnable action) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private NoticeChatMessageType resolveMessageType(NoticeChatMessage message) {
        if (message == null || message.getMessageType() == null) {
            return NoticeChatMessageType.TEXT;
        }
        return message.getMessageType();
    }

    private record RoomUpdatePayload(
            UUID ownerUserId,
            NoticeChatRoomResponse ownerPayload,
            UUID guestUserId,
            NoticeChatRoomResponse guestPayload
    ) {
    }
}
