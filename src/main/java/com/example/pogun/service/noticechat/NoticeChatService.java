package com.example.pogun.service.noticechat;

import com.example.pogun.dto.common.ApiResponse.ApiException;
import com.example.pogun.dto.noticechat.NoticeChatMessageEditRequest;
import com.example.pogun.dto.noticechat.NoticeChatMessagePageResponse;
import com.example.pogun.dto.noticechat.NoticeChatMessageRequest;
import com.example.pogun.dto.noticechat.NoticeChatMessageImageResponse;
import com.example.pogun.dto.noticechat.NoticeChatMessageImageProjection;
import com.example.pogun.dto.noticechat.NoticeChatMessageReplyResponse;
import com.example.pogun.dto.noticechat.NoticeChatMessageProjection;
import com.example.pogun.dto.noticechat.NoticeChatMessageResponse;
import com.example.pogun.dto.noticechat.NoticeChatImageOriginalResponse;
import com.example.pogun.dto.noticechat.NoticeChatReadRequest;
import com.example.pogun.dto.noticechat.NoticeChatRoomEventRequest;
import com.example.pogun.dto.noticechat.NoticeChatRoomCreateResult;
import com.example.pogun.dto.noticechat.NoticeChatRoomResponse;
import com.example.pogun.dto.noticechat.NoticeChatRoomSettingsRequest;
import com.example.pogun.dto.noticechat.NoticeChatTypingRequest;
import com.example.pogun.dto.storage.StoredImageVariant;
import com.example.pogun.entity.missingpet.PetNotice;
import com.example.pogun.entity.missingpet.PetNoticeImage;
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
import org.springframework.data.domain.PageRequest;
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
@RequiredArgsConstructor
public class NoticeChatService {
    private static final int MAX_IMAGE_COUNT = 10;
    private static final int DEFAULT_MESSAGE_PAGE_SIZE = 50;
    private static final int MAX_MESSAGE_PAGE_SIZE = 100;
    private static final String IMAGE_MESSAGE_PREVIEW = "사진을 보냈습니다";
    private static final String VIDEO_MESSAGE_PREVIEW = "동영상을 보냈습니다";

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
        List<NoticeChatRoomParticipantState> currentStates = participantStateRepository.findByUserAndLeftAtIsNull(currentUser);
        return buildRoomResponses(currentUser, currentStates);
    }

    @Transactional
    public List<NoticeChatRoomResponse> searchRooms(String keyword) {
        User currentUser = getCurrentUser();
        assertChatAvailableUser(currentUser, "채팅방 검색을 할 수 없습니다.");
        String normalizedKeyword = trimToNull(keyword);
        if (normalizedKeyword == null) {
            return getRooms();
        }
        List<NoticeChatRoomParticipantState> matchedStates = participantStateRepository
            .findByUserAndLeftAtIsNullAndKeyword(currentUser, currentUser.getId(), normalizedKeyword);
        return buildRoomResponses(currentUser, matchedStates);
    }

    @Transactional
    public NoticeChatRoomResponse getRoom(String roomId) {
        User currentUser = getCurrentUser();
        assertChatAvailableUser(currentUser, "채팅방 상세를 조회할 수 없습니다.");
        return toRoomResponse(getAccessibleRoom(roomId, currentUser), currentUser);
    }

    @Transactional
    public NoticeChatMessagePageResponse getMessages(String roomId, Long beforeSequence, Integer limit) {
        long startedAtNanos = System.nanoTime();
        User currentUser = getCurrentUser();
        assertChatAvailableUser(currentUser, "채팅 메시지를 조회할 수 없습니다.");
        NoticeChatRoom room = getAccessibleRoom(roomId, currentUser);
        log.debug("채팅 메시지 조회 시작 roomId={} userId={}", room.getId(), currentUser.getId());

        int pageSize = normalizeMessagePageSize(limit);
        List<NoticeChatMessageProjection> page = noticeChatMessageRepository.findVisiblePageRows(
                room,
                beforeSequence,
                PageRequest.of(0, pageSize + 1)
        );
        boolean hasMore = page.size() > pageSize;
        List<NoticeChatMessageProjection> messages = new ArrayList<>(hasMore ? page.subList(0, pageSize) : page);
        Collections.reverse(messages);
        latestOpponentMessage(messages, currentUser)
                .flatMap(message -> noticeChatMessageRepository
                        .findTopByRoomAndSenderUserNotAndDeletedAtIsNullAndRoomSequenceLessThanEqualOrderByRoomSequenceDescCreatedAtDesc(
                                room,
                                currentUser,
                                message.roomSequence()
                        ))
                .ifPresent(message -> markRoomAsRead(room, currentUser, message));
        Long opponentReadSequence = ensureParticipantState(room, getOpponent(room, currentUser)).getLastReadRoomSequence();
        Map<UUID, List<NoticeChatMessageImageResponse>> imagesByMessageId = findImagesByMessageId(messages);
        Map<UUID, NoticeChatMessageImageResponse> replyThumbnailByMessageId = findFirstImageByMessageId(messages.stream()
                .map(NoticeChatMessageProjection::replyMessageId)
                .filter(java.util.Objects::nonNull)
                .toList());

        log.debug("채팅 메시지 조회 완료 roomId={} messageCount={} userId={} elapsedMs={}",
                room.getId(), messages.size(), currentUser.getId(), elapsedMillis(startedAtNanos));
        Long nextBeforeSequence = hasMore && !messages.isEmpty() ? messages.get(0).roomSequence() : null;
        return new NoticeChatMessagePageResponse(
                messages.stream()
                        .map(message -> {
                            NoticeChatMessageImageResponse replyThumbnail = message.replyMessageId() != null
                                    ? replyThumbnailByMessageId.get(message.replyMessageId())
                                    : null;
                            return toMessageResponse(
                                    message,
                                    currentUser,
                                    opponentReadSequence,
                                    imagesByMessageId.getOrDefault(message.id(), List.of()),
                                    replyThumbnail
                            );
                        })
                        .toList(),
                hasMore,
                nextBeforeSequence,
                pageSize
        );
    }

    @Transactional
    public NoticeChatRoomResponse markRoomAsRead(String roomId) {
        User currentUser = getCurrentUser();
        assertChatAvailableUser(currentUser, "채팅방 읽음 처리를 할 수 없습니다.");
        NoticeChatRoom room = getAccessibleRoom(roomId, currentUser);
        NoticeChatMessage latestOpponentMessage = noticeChatMessageRepository
                .findTopByRoomAndSenderUserNotAndDeletedAtIsNullOrderByRoomSequenceDescCreatedAtDesc(room, currentUser)
                .orElse(null);
        markRoomAsRead(room, currentUser, latestOpponentMessage);
        return toRoomResponse(room, currentUser);
    }

    @Transactional
    public void markRoomAsRead(Principal principal, NoticeChatReadRequest request) {
        User currentUser = getEventUser(principal, "채팅방 읽음 처리를 할 수 없습니다.");
        NoticeChatRoom room = getAccessibleRoom(request.getRoomId().toString(), currentUser);
        NoticeChatMessage latestOpponentMessage = resolveLatestReadableOpponentMessage(room, currentUser, request);
        markRoomAsRead(room, currentUser, latestOpponentMessage);
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
        return noticeChatMessageRepository.findByRoomAndDeletedAtIsNullAndMessageContainingIgnoreCaseOrderByRoomSequenceAscCreatedAtAsc(room, normalizedKeyword).stream()
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
        long startedAtNanos = System.nanoTime();
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
        log.debug("채팅 텍스트 메시지 처리 완료 roomId={} senderUserId={} elapsedMs={}",
                room.getId(), sender.getId(), elapsedMillis(startedAtNanos));
        return broadcastMessage(room, sender, saved);
    }

    @Transactional
    public NoticeChatMessageResponse updateMessage(String roomId, String messageId, NoticeChatMessageEditRequest request) {
        User currentUser = getCurrentUser();
        assertChatAvailableUser(currentUser, "메시지를 수정할 수 없습니다.");
        NoticeChatRoom room = getAccessibleRoom(roomId, currentUser);
        assertRoomAcceptsConversation(room);
        User opponent = getOpponent(room, currentUser);
        assertOpponentCanReceiveChat(opponent);
        assertNotBlockedEitherDirection(currentUser, opponent);
        NoticeChatMessage message = getEditableMessage(messageId, room, currentUser);
        if (message.getDeletedAt() != null) {
            throw ApiException.conflict("CHAT_MESSAGE_DELETED", "삭제된 메시지는 수정할 수 없습니다.");
        }
        String content = trimToNull(request.getMessage());
        if (content == null) {
            throw ApiException.badRequest("EMPTY_MESSAGE", "메시지 내용은 비어 있을 수 없습니다.");
        }
        if (content.length() > 2000) {
            throw ApiException.badRequest("MESSAGE_TOO_LONG", "message는 2000자를 초과할 수 없습니다.");
        }
        message.setMessage(content);
        message.setEditedAt(Instant.now());
        NoticeChatMessage saved = noticeChatMessageRepository.saveAndFlush(message);
        refreshLastMessage(room);
        noticeChatRoomRepository.save(room);
        return broadcastMessageUpdate(room, currentUser, saved);
    }

    @Transactional
    public NoticeChatMessageResponse deleteMessage(String roomId, String messageId) {
        User currentUser = getCurrentUser();
        assertChatAvailableUser(currentUser, "메시지를 삭제할 수 없습니다.");
        NoticeChatRoom room = getAccessibleRoom(roomId, currentUser);
        assertRoomAcceptsConversation(room);
        User opponent = getOpponent(room, currentUser);
        assertOpponentCanReceiveChat(opponent);
        assertNotBlockedEitherDirection(currentUser, opponent);
        NoticeChatMessage message = getEditableMessage(messageId, room, currentUser);
        if (message.getDeletedAt() == null) {
            message.setDeletedAt(Instant.now());
            message.setEditedAt(null);
            NoticeChatMessage saved = noticeChatMessageRepository.saveAndFlush(message);
            refreshLastMessage(room);
            noticeChatRoomRepository.save(room);
            return broadcastMessageDeleted(room, currentUser, saved);
        }
        return toMessageResponse(message, currentUser);
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
        NoticeChatMessageType messageType = containsVideo(imageVariants) ? NoticeChatMessageType.VIDEO : NoticeChatMessageType.IMAGE;
        NoticeChatMessage saved = saveMessage(
                room,
                sender,
                messageType,
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
        noticeChatMessageRepository.findTopByRoomAndSenderUserNotAndDeletedAtIsNullOrderByRoomSequenceDescCreatedAtDesc(room, user)
                .ifPresent(message -> markRoomAsRead(room, user, message));
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
        return toRoomResponse(room, currentUser, currentState, opponentState, lastMessageType,
                resolveUnreadCount(room, currentUser, currentState));
    }

    private NoticeChatRoomResponse toRoomResponse(
            NoticeChatRoom room,
            User currentUser,
            NoticeChatRoomParticipantState currentState,
            NoticeChatRoomParticipantState opponentState
    ) {
        NoticeChatMessageType lastMessageType = resolveLastMessageType(room);
        return toRoomResponse(room, currentUser, currentState, opponentState, lastMessageType,
            resolveUnreadCount(room, currentUser, currentState));
    }

    private NoticeChatRoomResponse toRoomResponse(
            NoticeChatRoom room,
            User currentUser,
            NoticeChatRoomParticipantState currentState,
            NoticeChatRoomParticipantState opponentState,
            NoticeChatMessageType lastMessageType,
            long unreadCount
    ) {
        User opponent = getOpponent(room, currentUser);
        return new NoticeChatRoomResponse(
                room.getId(),
                room.getNotice().getId(),
                room.getNotice().getTitle(),
            resolveNoticeImageUrl(room),
            room.getNotice().getStatus() != null ? room.getNotice().getStatus().name() : null,
            resolveNoticeStatusLabel(room.getNotice()),
            room.getNotice().getMissingRegion(),
                room.getStatus().name(),
                lastMessageType != null ? lastMessageType.name() : null,
                room.getLastMessageAt(),
                resolveLastMessagePreview(room, lastMessageType),
                room.getCreatedAt(),
                opponent.getId(),
                opponent.getNickname(),
                unreadCount,
                currentState.getNotificationEnabled(),
                currentState.getFavorite(),
                currentState.getPinned(),
                currentState.getLeftAt(),
                opponentState.getOnline(),
                opponentState.getLastActiveAt(),
                currentState.getLastReadMessage() != null ? currentState.getLastReadMessage().getId() : null,
                currentState.getLastReadAt(),
                currentState.getLastReadRoomSequence()
        );
    }

    private String resolveNoticeImageUrl(NoticeChatRoom room) {
        if (room == null || room.getNotice() == null || room.getNotice().getImages() == null) {
            return null;
        }
        return room.getNotice().getImages().stream()
                .map(PetNoticeImage::getImageUrl)
                .filter(url -> url != null && !url.isBlank())
                .findFirst()
                .orElse(null);
    }

    private String resolveNoticeStatusLabel(PetNotice notice) {
        if (notice == null || notice.getStatus() == null) {
            return null;
        }
        return switch (notice.getStatus()) {
            case OPEN -> "공개중";
            case RESOLVED -> "해결됨";
            case CLOSED -> "종료";
        };
    }

            private List<NoticeChatRoomResponse> buildRoomResponses(User currentUser, List<NoticeChatRoomParticipantState> currentStates) {
            List<NoticeChatRoomParticipantState> validStates = currentStates.stream()
                .filter(state -> state.getRoom() != null && state.getRoom().getNotice() != null)
                .sorted(participantStateComparator())
                .toList();
            if (validStates.isEmpty()) {
                return List.of();
            }

            List<NoticeChatRoom> rooms = validStates.stream()
                .map(NoticeChatRoomParticipantState::getRoom)
                .toList();

            Map<UUID, Long> unreadCountByRoomId = noticeChatMessageRepository
                .findUnreadCountsByStateUserAndRooms(currentUser, rooms)
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                    com.example.pogun.dto.noticechat.NoticeChatRoomUnreadCountProjection::roomId,
                    com.example.pogun.dto.noticechat.NoticeChatRoomUnreadCountProjection::unreadCount
                ));

            Map<UUID, NoticeChatRoomParticipantState> opponentStateByRoomId = participantStateRepository.findByRoomIn(rooms)
                .stream()
                .filter(state -> state.getUser() != null && !state.getUser().getId().equals(currentUser.getId()))
                .collect(java.util.stream.Collectors.toMap(
                    state -> state.getRoom().getId(),
                    state -> state,
                    (existing, ignored) -> existing
                ));

            return validStates.stream()
                .map(currentState -> {
                    NoticeChatRoom room = currentState.getRoom();
                    NoticeChatRoomParticipantState opponentState = opponentStateByRoomId.get(room.getId());
                    if (opponentState == null) {
                    opponentState = ensureParticipantState(room, getOpponent(room, currentUser));
                    }
                    NoticeChatMessageType lastMessageType = resolveLastMessageType(room);
                    long unreadCount = unreadCountByRoomId.getOrDefault(room.getId(), 0L);
                    return toRoomResponse(room, currentUser, currentState, opponentState, lastMessageType, unreadCount);
                })
                .toList();
            }

    private NoticeChatMessageResponse toMessageResponse(NoticeChatMessage message, User currentUser) {
        User opponent = getOpponent(message.getRoom(), currentUser);
        NoticeChatRoomParticipantState opponentState = ensureParticipantState(message.getRoom(), opponent);
        return toMessageResponse(message, currentUser, opponentState.getLastReadRoomSequence());
    }

    private NoticeChatMessageResponse toMessageResponse(
            NoticeChatMessageProjection message,
            User currentUser,
            Long opponentReadSequence,
            List<NoticeChatMessageImageResponse> images,
            NoticeChatMessageImageResponse replyThumbnail
    ) {
        NoticeChatMessageType messageType = message.messageType() != null ? message.messageType() : NoticeChatMessageType.TEXT;
        boolean deleted = message.deletedAt() != null;
        boolean mine = message.senderUserId().equals(currentUser.getId());
        Boolean read = resolveRead(message.isRead(), message.roomSequence(), mine, opponentReadSequence);
        return new NoticeChatMessageResponse(
                message.id(),
                message.roomId(),
                message.senderUserId(),
                message.senderNickname(),
                deleted ? null : message.message(),
                messageType.name(),
                deleted ? List.of() : images,
                toReplyResponse(message, replyThumbnail),
                read,
                mine,
                message.createdAt(),
                message.clientMessageId(),
                message.roomSequence(),
                message.createdAt(),
                message.editedAt(),
                message.deletedAt(),
                deleted
        );
    }

    private NoticeChatMessageResponse toMessageResponse(
            NoticeChatMessage message,
            User currentUser,
            Long opponentReadSequence
    ) {
        NoticeChatMessageType messageType = resolveMessageType(message);
        boolean deleted = message.getDeletedAt() != null;
        boolean mine = message.getSenderUser().getId().equals(currentUser.getId());
        Boolean read = resolveRead(message, mine, opponentReadSequence);
        return new NoticeChatMessageResponse(
                message.getId(),
                message.getRoom().getId(),
                message.getSenderUser().getId(),
                message.getSenderUser().getNickname(),
                deleted ? null : message.getMessage(),
                messageType.name(),
                deleted ? List.of() : message.getImages().stream()
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
                read,
                mine,
                message.getCreatedAt(),
                message.getClientMessageId(),
                message.getRoomSequence(),
                message.getCreatedAt(),
                message.getEditedAt(),
                message.getDeletedAt(),
                deleted
        );
    }

    private Boolean resolveRead(NoticeChatMessage message, boolean mine, Long opponentReadSequence) {
        return resolveRead(message.getIsRead(), message.getRoomSequence(), mine, opponentReadSequence);
    }

    private Boolean resolveRead(Boolean fallbackRead, Long roomSequence, boolean mine, Long opponentReadSequence) {
        if (!mine) {
            return fallbackRead;
        }
        if (opponentReadSequence == null || roomSequence == null) {
            return fallbackRead;
        }
        return opponentReadSequence >= roomSequence;
    }

    private Map<UUID, List<NoticeChatMessageImageResponse>> findImagesByMessageId(List<NoticeChatMessageProjection> messages) {
        List<UUID> messageIds = messages.stream()
                .map(NoticeChatMessageProjection::id)
                .toList();
        if (messageIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<NoticeChatMessageImageResponse>> imagesByMessageId = new LinkedHashMap<>();
        for (NoticeChatMessageImageProjection image : noticeChatMessageImageRepository.findProjectedByMessageIds(messageIds)) {
            imagesByMessageId.computeIfAbsent(image.messageId(), ignored -> new ArrayList<>())
                    .add(new NoticeChatMessageImageResponse(
                            image.id(),
                            image.imageUrl(),
                            image.webpUrl() != null ? image.webpUrl() : image.imageUrl(),
                            image.mediumUrl(),
                            image.thumbnailUrl(),
                            image.previewUrl(),
                            image.displayOrder()
                    ));
        }
        return imagesByMessageId;
    }

    private Map<UUID, NoticeChatMessageImageResponse> findFirstImageByMessageId(List<UUID> messageIds) {
        if (messageIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, NoticeChatMessageImageResponse> firstImageByMessageId = new LinkedHashMap<>();
        for (NoticeChatMessageImageProjection image : noticeChatMessageImageRepository.findProjectedByMessageIds(messageIds)) {
            firstImageByMessageId.computeIfAbsent(image.messageId(), ignored -> new NoticeChatMessageImageResponse(
                    image.id(),
                    image.imageUrl(),
                    image.webpUrl() != null ? image.webpUrl() : image.imageUrl(),
                    image.mediumUrl(),
                    image.thumbnailUrl(),
                    image.previewUrl(),
                    image.displayOrder()
            ));
        }
        return firstImageByMessageId;
    }

    private long resolveUnreadCount(NoticeChatRoom room, User currentUser, NoticeChatRoomParticipantState currentState) {
        Long lastReadRoomSequence = currentState.getLastReadRoomSequence();
        if (lastReadRoomSequence == null) {
            return noticeChatMessageRepository.countByRoomAndSenderUserNotAndDeletedAtIsNull(room, currentUser);
        }
        return noticeChatMessageRepository.countByRoomAndSenderUserNotAndDeletedAtIsNullAndRoomSequenceGreaterThan(
                room,
                currentUser,
                lastReadRoomSequence
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

    private int normalizeMessagePageSize(Integer limit) {
        if (limit == null) {
            return DEFAULT_MESSAGE_PAGE_SIZE;
        }
        return Math.max(1, Math.min(limit, MAX_MESSAGE_PAGE_SIZE));
    }

    private Optional<NoticeChatMessageProjection> latestOpponentMessage(List<NoticeChatMessageProjection> messages, User currentUser) {
        return messages.stream()
                .filter(message -> !message.senderUserId().equals(currentUser.getId()))
                .filter(message -> message.roomSequence() != null)
                .reduce((first, second) -> second);
    }

    private NoticeChatMessage resolveLatestReadableOpponentMessage(
            NoticeChatRoom room,
            User currentUser,
            NoticeChatReadRequest request
    ) {
        if (request.getLastReadRoomSequence() != null) {
            return noticeChatMessageRepository
                    .findTopByRoomAndSenderUserNotAndDeletedAtIsNullAndRoomSequenceLessThanEqualOrderByRoomSequenceDescCreatedAtDesc(
                            room,
                            currentUser,
                            request.getLastReadRoomSequence()
                    )
                    .orElse(null);
        }
        if (request.getLastReadMessageId() != null) {
            NoticeChatMessage message = noticeChatMessageRepository.findById(request.getLastReadMessageId())
                    .orElseThrow(() -> ApiException.notFound("CHAT_MESSAGE_NOT_FOUND", "채팅 메시지를 찾을 수 없습니다."));
            if (!message.getRoom().getId().equals(room.getId())) {
                throw ApiException.badRequest("INVALID_CHAT_MESSAGE", "해당 채팅방의 메시지가 아닙니다.");
            }
            if (message.getSenderUser().getId().equals(currentUser.getId())) {
                return null;
            }
            if (message.getDeletedAt() != null) {
                return null;
            }
            return message;
        }
        return noticeChatMessageRepository
                .findTopByRoomAndSenderUserNotAndDeletedAtIsNullOrderByRoomSequenceDescCreatedAtDesc(room, currentUser)
                .orElse(null);
    }

    private String resolveLastMessagePreview(NoticeChatRoom room, NoticeChatMessageType lastMessageType) {
        if (room.getLastMessagePreview() != null && !room.getLastMessagePreview().isBlank()) {
            return room.getLastMessagePreview();
        }
        if (lastMessageType == NoticeChatMessageType.IMAGE) {
            return IMAGE_MESSAGE_PREVIEW;
        }
        if (lastMessageType == NoticeChatMessageType.VIDEO) {
            return VIDEO_MESSAGE_PREVIEW;
        }
        return null;
    }

    private NoticeChatMessageType resolveLastMessageType(NoticeChatRoom room) {
        return room.getLastMessageType();
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
            Long latestReadRoomSequence,
            int readCount
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "READ_RECEIPT");
        payload.put("roomId", roomId);
        payload.put("readerUserId", readerUserId);
        payload.put("latestReadMessageId", latestReadMessageId);
        payload.put("latestReadRoomSequence", latestReadRoomSequence);
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
        NoticeChatRoomParticipantState ownerState = ensureParticipantState(room, room.getOwnerUser());
        NoticeChatRoomParticipantState guestState = ensureParticipantState(room, room.getGuestUser());
        return new RoomUpdatePayload(
                room.getOwnerUser().getId(),
                toRoomResponse(room, room.getOwnerUser(), ownerState, guestState),
                room.getGuestUser().getId(),
                toRoomResponse(room, room.getGuestUser(), guestState, ownerState)
        );
    }

    private void broadcastRoomUpdate(RoomUpdatePayload roomUpdatePayload) {
        simpMessagingTemplate.convertAndSend(userRoomsTopic(roomUpdatePayload.ownerUserId()), roomUpdatePayload.ownerPayload());
        simpMessagingTemplate.convertAndSend(userRoomsTopic(roomUpdatePayload.guestUserId()), roomUpdatePayload.guestPayload());
    }

    private RoomPatchPayload buildRoomPatchPayload(NoticeChatRoom room) {
        NoticeChatRoomParticipantState ownerState = ensureParticipantState(room, room.getOwnerUser());
        NoticeChatRoomParticipantState guestState = ensureParticipantState(room, room.getGuestUser());
        return new RoomPatchPayload(
                room.getOwnerUser().getId(),
                toRoomPatch(room, room.getOwnerUser(), ownerState),
                room.getGuestUser().getId(),
                toRoomPatch(room, room.getGuestUser(), guestState)
        );
    }

    private Map<String, Object> toRoomPatch(
            NoticeChatRoom room,
            User currentUser,
            NoticeChatRoomParticipantState currentState
    ) {
        NoticeChatMessageType lastMessageType = resolveLastMessageType(room);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "ROOM_PATCH");
        payload.put("roomId", room.getId());
        payload.put("lastMessageType", lastMessageType != null ? lastMessageType.name() : null);
        payload.put("lastMessageAt", room.getLastMessageAt());
        payload.put("lastMessagePreview", resolveLastMessagePreview(room, lastMessageType));
        payload.put("unreadCount", resolveUnreadCount(room, currentUser, currentState));
        payload.put("lastReadMessageId", currentState.getLastReadMessage() != null ? currentState.getLastReadMessage().getId() : null);
        payload.put("lastReadAt", currentState.getLastReadAt());
        payload.put("lastReadRoomSequence", currentState.getLastReadRoomSequence());
        return payload;
    }

    private void broadcastRoomPatch(RoomPatchPayload roomPatchPayload) {
        simpMessagingTemplate.convertAndSend(userRoomsTopic(roomPatchPayload.ownerUserId()), (Object) roomPatchPayload.ownerPayload());
        simpMessagingTemplate.convertAndSend(userRoomsTopic(roomPatchPayload.guestUserId()), (Object) roomPatchPayload.guestPayload());
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
        NoticeChatRoom lockedRoom = lockRoomForMessageWrite(room);
        long nextRoomSequence = (lockedRoom.getLastMessageSequence() == null ? 0L : lockedRoom.getLastMessageSequence()) + 1;
        List<NoticeChatMessageImage> images = new ArrayList<>();
        NoticeChatMessage message = NoticeChatMessage.builder()
                .room(lockedRoom)
                .senderUser(sender)
                .messageType(messageType)
                .message(content)
                .replyToMessage(replyToMessage)
                .clientMessageId(clientMessageId)
                .roomSequence(nextRoomSequence)
                .images(images)
                .build();
        for (int index = 0; index < imageVariants.size(); index++) {
            StoredImageVariant variant = imageVariants.get(index);
            images.add(NoticeChatMessageImage.builder()
                    .message(message)
                    .imageUrl(variant.webpUrl() != null ? variant.webpUrl() : variant.originalUrl())
                    .originalUrl(variant.originalUrl())
                    .webpUrl(variant.webpUrl())
                    .mediumUrl(variant.mediumUrl())
                    .thumbnailUrl(variant.thumbnailUrl())
                    .previewUrl(variant.previewUrl())
                    .displayOrder(index)
                    .build());
        }

        NoticeChatMessage saved = noticeChatMessageRepository.saveAndFlush(message);
        applyLastMessage(lockedRoom, saved);
        noticeChatRoomRepository.save(lockedRoom);
        log.info("채팅 메시지 저장 완료 roomId={} messageId={} senderUserId={} type={} createdAt={}",
                room.getId(), saved.getId(), sender.getId(), saved.getMessageType(), saved.getCreatedAt());
        return saved;
    }

    private NoticeChatRoom lockRoomForMessageWrite(NoticeChatRoom room) {
        return noticeChatRoomRepository.findByIdForUpdate(room.getId())
                .orElseThrow(() -> ApiException.notFound("CHAT_ROOM_NOT_FOUND", "채팅방을 찾을 수 없습니다."));
    }

    private void markRoomAsRead(NoticeChatRoom room, User reader, NoticeChatMessage latestReadMessage) {
        long startedAtNanos = System.nanoTime();
        if (latestReadMessage == null || latestReadMessage.getRoomSequence() == null) {
            return;
        }
        NoticeChatRoomParticipantState state = ensureParticipantState(room, reader);
        Long previousReadSequence = state.getLastReadRoomSequence();
        if (previousReadSequence != null && previousReadSequence >= latestReadMessage.getRoomSequence()) {
            return;
        }
        state.setLastReadMessage(latestReadMessage);
        state.setLastReadAt(Instant.now());
        state.setLastReadRoomSequence(latestReadMessage.getRoomSequence());
        participantStateRepository.save(state);

        UUID readRoomId = room.getId();
        UUID readerUserId = reader.getId();
        UUID opponentUserId = getOpponent(room, reader).getId();
        UUID latestReadMessageId = latestReadMessage.getId();
        Long latestReadRoomSequence = latestReadMessage.getRoomSequence();
        int readCount = 1;
        afterCommitOrNow(() -> sendReadReceipt(
                readRoomId,
                readerUserId,
                opponentUserId,
                latestReadMessageId,
                latestReadRoomSequence,
                readCount
        ));
        log.debug("채팅 읽음 watermark 처리 완료 roomId={} readerUserId={} sequence={} elapsedMs={}",
                room.getId(), reader.getId(), latestReadRoomSequence, elapsedMillis(startedAtNanos));
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

    private Comparator<NoticeChatRoomParticipantState> participantStateComparator() {
        return Comparator
                .comparing((NoticeChatRoomParticipantState state) -> Boolean.TRUE.equals(state.getPinned())).reversed()
                .thenComparing(state -> {
                    NoticeChatRoom room = state.getRoom();
                    return room.getLastMessageAt() != null ? room.getLastMessageAt() : room.getCreatedAt();
                }, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(state -> state.getRoom().getCreatedAt(), Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private NoticeChatMessageResponse broadcastMessage(NoticeChatRoom room, User sender, NoticeChatMessage saved) {
        return broadcastMessageUpdate(room, sender, saved);
    }

    private NoticeChatMessageResponse broadcastMessageUpdate(NoticeChatRoom room, User sender, NoticeChatMessage saved) {
        User opponent = getOpponent(room, sender);
        NoticeChatMessageResponse senderPayload = toMessageResponse(saved, sender);
        NoticeChatMessageResponse opponentPayload = toMessageResponse(saved, opponent);
        UUID roomId = room.getId();
        UUID ownerUserId = room.getOwnerUser().getId();
        UUID guestUserId = room.getGuestUser().getId();
        NoticeChatMessageType lastMessageType = room.getLastMessageType();
        Instant lastMessageAt = room.getLastMessageAt();
        String lastMessagePreview = room.getLastMessagePreview();

        afterCommitOrNow(() -> {
            simpMessagingTemplate.convertAndSend(userRoomTopic(sender.getId(), roomId), senderPayload);
            simpMessagingTemplate.convertAndSend(userRoomTopic(opponent.getId(), roomId), opponentPayload);
        });
        afterCommitOrNow(() -> CompletableFuture.runAsync(() -> {
            RoomPatchPayload roomPatchPayload = buildLightweightRoomPatchPayload(
                    ownerUserId,
                    guestUserId,
                    roomId,
                    lastMessageType,
                    lastMessageAt,
                    lastMessagePreview
            );
            broadcastRoomPatch(roomPatchPayload);
        }));
        return senderPayload;
    }

    private RoomPatchPayload buildLightweightRoomPatchPayload(
            UUID ownerUserId,
            UUID guestUserId,
            UUID roomId,
            NoticeChatMessageType lastMessageType,
            Instant lastMessageAt,
            String lastMessagePreview
    ) {
        Map<String, Object> ownerPayload = toLightweightRoomPatch(roomId, lastMessageType, lastMessageAt, lastMessagePreview);
        Map<String, Object> guestPayload = toLightweightRoomPatch(roomId, lastMessageType, lastMessageAt, lastMessagePreview);
        return new RoomPatchPayload(ownerUserId, ownerPayload, guestUserId, guestPayload);
    }

    private Map<String, Object> toLightweightRoomPatch(
            UUID roomId,
            NoticeChatMessageType lastMessageType,
            Instant lastMessageAt,
            String lastMessagePreview
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "ROOM_PATCH");
        payload.put("roomId", roomId);
        payload.put("lastMessageType", lastMessageType != null ? lastMessageType.name() : null);
        payload.put("lastMessageAt", lastMessageAt);
        payload.put("lastMessagePreview", lastMessagePreview);
        return payload;
    }

    private NoticeChatMessageResponse broadcastMessageDeleted(NoticeChatRoom room, User sender, NoticeChatMessage saved) {
        User opponent = getOpponent(room, sender);
        NoticeChatMessageResponse senderPayload = toMessageResponse(saved, sender);
        RoomPatchPayload roomPatchPayload = buildRoomPatchPayload(room);
        Map<String, Object> deletePayload = new LinkedHashMap<>();
        deletePayload.put("type", "MESSAGE_DELETED");
        deletePayload.put("roomId", room.getId());
        deletePayload.put("messageId", saved.getId());
        deletePayload.put("senderUserId", sender.getId());
        deletePayload.put("deletedAt", saved.getDeletedAt());

        afterCommitOrNow(() -> {
            simpMessagingTemplate.convertAndSend(userRoomTopic(sender.getId(), room.getId()), (Object) deletePayload);
            simpMessagingTemplate.convertAndSend(userRoomTopic(opponent.getId(), room.getId()), (Object) deletePayload);
            broadcastRoomPatch(roomPatchPayload);
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
        if (resolveMessageType(message) == NoticeChatMessageType.VIDEO) {
            return VIDEO_MESSAGE_PREVIEW;
        }
        return toTextPreview(message.getMessage());
    }

    private String toTextPreview(String message) {
        String trimmed = trimToNull(message);
        if (trimmed == null) {
            return null;
        }
        return trimmed.length() <= 80 ? trimmed : trimmed.substring(0, 77) + "...";
    }

    private NoticeChatMessageReplyResponse toReplyResponse(NoticeChatMessage replyToMessage) {
        if (replyToMessage == null) {
            return null;
        }
        if (replyToMessage.getDeletedAt() != null) {
            return null;
        }
        NoticeChatMessageType replyMessageType = resolveMessageType(replyToMessage);
        String preview = replyMessageType == NoticeChatMessageType.IMAGE
                ? "사진"
                : replyMessageType == NoticeChatMessageType.VIDEO ? "동영상" : toPreview(replyToMessage);
        return new NoticeChatMessageReplyResponse(
                replyToMessage.getId(),
                replyToMessage.getSenderUser().getId(),
                replyToMessage.getSenderUser().getNickname(),
                preview,
                replyMessageType.name(),
                firstAttachmentPreviewUrl(replyToMessage)
        );
    }

    private NoticeChatMessageReplyResponse toReplyResponse(
            NoticeChatMessageProjection message,
            NoticeChatMessageImageResponse replyThumbnail
    ) {
        if (message.replyMessageId() == null || message.replyDeletedAt() != null) {
            return null;
        }
        NoticeChatMessageType replyMessageType = message.replyMessageType() != null
                ? message.replyMessageType()
                : NoticeChatMessageType.TEXT;
        String preview = replyMessageType == NoticeChatMessageType.IMAGE
                ? "사진"
                : replyMessageType == NoticeChatMessageType.VIDEO ? "동영상" : toTextPreview(message.replyMessage());
        return new NoticeChatMessageReplyResponse(
                message.replyMessageId(),
                message.replySenderUserId(),
                message.replySenderNickname(),
                preview,
                replyMessageType.name(),
                bestAttachmentPreviewUrl(replyThumbnail)
        );
    }

    private String firstAttachmentPreviewUrl(NoticeChatMessage message) {
        if (message == null || message.getImages() == null || message.getImages().isEmpty()) {
            return null;
        }
        return message.getImages().stream()
                .min(Comparator.comparingInt(NoticeChatMessageImage::getDisplayOrder))
                .map(image -> firstNonBlank(
                        image.getThumbnailUrl(),
                        image.getPreviewUrl(),
                        image.getWebpUrl(),
                        image.getImageUrl()
                ))
                .orElse(null);
    }

    private String bestAttachmentPreviewUrl(NoticeChatMessageImageResponse image) {
        if (image == null) {
            return null;
        }
        return firstNonBlank(image.thumbnailUrl(), image.previewUrl(), image.webpUrl(), image.imageUrl());
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null) {
                return trimmed;
            }
        }
        return null;
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

    private NoticeChatMessage getEditableMessage(String messageId, NoticeChatRoom room, User currentUser) {
        NoticeChatMessage message = getMessage(messageId);
        if (!message.getRoom().getId().equals(room.getId())) {
            throw ApiException.badRequest("INVALID_CHAT_MESSAGE", "해당 채팅방의 메시지가 아닙니다.");
        }
        if (!message.getSenderUser().getId().equals(currentUser.getId())) {
            throw ApiException.forbidden("CHAT_MESSAGE_FORBIDDEN", "본인이 보낸 메시지만 변경할 수 있습니다.");
        }
        return message;
    }

    private NoticeChatMessage getMessage(String messageId) {
        try {
            return noticeChatMessageRepository.findById(UUID.fromString(messageId))
                    .orElseThrow(() -> ApiException.notFound("CHAT_MESSAGE_NOT_FOUND", "채팅 메시지를 찾을 수 없습니다."));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("INVALID_CHAT_MESSAGE_ID", "올바르지 않은 채팅 메시지 ID 형식입니다.");
        }
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

    private long elapsedMillis(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000;
    }

    private NoticeChatMessageType resolveMessageType(NoticeChatMessage message) {
        if (message == null || message.getMessageType() == null) {
            return NoticeChatMessageType.TEXT;
        }
        return message.getMessageType();
    }

    private void refreshLastMessage(NoticeChatRoom room) {
        noticeChatMessageRepository.findTopByRoomAndDeletedAtIsNullOrderByCreatedAtDesc(room)
                .ifPresentOrElse(
                        message -> applyLastVisibleMessage(room, message),
                        () -> {
                            room.setLastMessageAt(null);
                            room.setLastMessageType(null);
                            room.setLastMessagePreview(null);
                        }
                );
    }

    private void applyLastMessage(NoticeChatRoom room, NoticeChatMessage message) {
        applyLastVisibleMessage(room, message);
        room.setLastMessageSequence(message.getRoomSequence());
    }

    private void applyLastVisibleMessage(NoticeChatRoom room, NoticeChatMessage message) {
        room.setLastMessageAt(message.getCreatedAt() != null ? message.getCreatedAt() : Instant.now());
        room.setLastMessageType(message.getMessageType());
        room.setLastMessagePreview(toPreview(message));
    }

    private boolean containsVideo(List<StoredImageVariant> variants) {
        return variants.stream()
                .map(StoredImageVariant::originalUrl)
                .filter(url -> url != null)
                .anyMatch(url -> url.toLowerCase().endsWith(".mp4"));
    }

    private record RoomUpdatePayload(
            UUID ownerUserId,
            NoticeChatRoomResponse ownerPayload,
            UUID guestUserId,
            NoticeChatRoomResponse guestPayload
    ) {
    }

    private record RoomPatchPayload(
            UUID ownerUserId,
            Map<String, Object> ownerPayload,
            UUID guestUserId,
            Map<String, Object> guestPayload
    ) {
    }
}
