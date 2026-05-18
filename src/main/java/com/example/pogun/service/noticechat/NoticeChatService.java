package com.example.pogun.service.noticechat;

import com.example.pogun.dto.common.ApiResponse.ApiException;
import com.example.pogun.dto.noticechat.NoticeChatMessageRequest;
import com.example.pogun.dto.noticechat.NoticeChatMessageImageResponse;
import com.example.pogun.dto.noticechat.NoticeChatMessagePageResponse;
import com.example.pogun.dto.noticechat.NoticeChatMessageReplyResponse;
import com.example.pogun.dto.noticechat.NoticeChatMessageResponse;
import com.example.pogun.dto.noticechat.NoticeChatMessageUpdateRequest;
import com.example.pogun.dto.noticechat.NoticeChatImageOriginalResponse;
import com.example.pogun.dto.noticechat.NoticeChatReadRequest;
import com.example.pogun.dto.noticechat.NoticeChatRoomEventRequest;
import com.example.pogun.dto.noticechat.NoticeChatRoomCreateResult;
import com.example.pogun.dto.noticechat.NoticeChatRoomResponse;
import com.example.pogun.dto.noticechat.NoticeChatRoomSettingsRequest;
import com.example.pogun.dto.noticechat.NoticeChatTypingRequest;
import com.example.pogun.dto.storage.StoredImageVariant;
import com.example.pogun.entity.missingpet.PetNotice;
import com.example.pogun.entity.missingpet.enums.PetNoticeStatus;
import com.example.pogun.entity.notification.enums.NotificationPriority;
import com.example.pogun.entity.notification.enums.NotificationTargetType;
import com.example.pogun.entity.notification.enums.NotificationType;
import com.example.pogun.entity.noticechat.NoticeChatMessage;
import com.example.pogun.entity.noticechat.NoticeChatMessageImage;
import com.example.pogun.entity.noticechat.NoticeChatReadReceipt;
import com.example.pogun.entity.noticechat.NoticeChatRoom;
import com.example.pogun.entity.noticechat.NoticeChatRoomParticipantState;
import com.example.pogun.entity.noticechat.enums.NoticeChatMessageType;
import com.example.pogun.entity.noticechat.enums.NoticeChatRoomStatus;
import com.example.pogun.entity.user.User;
import com.example.pogun.entity.user.enums.UserAvailabilityStatus;
import com.example.pogun.entity.user.enums.UserStatus;
import com.example.pogun.repository.missingpet.PetNoticeRepository;
import com.example.pogun.repository.noticechat.NoticeChatMessageRepository;
import com.example.pogun.repository.noticechat.NoticeChatMessageImageRepository;
import com.example.pogun.repository.noticechat.NoticeChatReadReceiptRepository;
import com.example.pogun.repository.noticechat.NoticeChatRoomParticipantStateRepository;
import com.example.pogun.repository.noticechat.NoticeChatRoomRepository;
import com.example.pogun.repository.user.UserBlockRepository;
import com.example.pogun.repository.user.UserRepository;
import com.example.pogun.service.notification.NotificationService;
import com.example.pogun.service.storage.S3ImageStorageService;
import com.example.pogun.service.user.UserPresenceService;
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
import java.util.Collections;
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
    private static final long MAX_TOTAL_MEDIA_SIZE = 30L * 1024L * 1024L;
    private static final int DEFAULT_MESSAGE_PAGE_LIMIT = 50;
    private static final int MAX_MESSAGE_PAGE_LIMIT = 100;
    private static final String IMAGE_MESSAGE_PREVIEW = "사진을 보냈습니다";
    private static final String VIDEO_MESSAGE_PREVIEW = "동영상을 보냈습니다";

    private final NoticeChatRoomRepository noticeChatRoomRepository;
    private final NoticeChatMessageRepository noticeChatMessageRepository;
    private final NoticeChatMessageImageRepository noticeChatMessageImageRepository;
    private final NoticeChatReadReceiptRepository noticeChatReadReceiptRepository;
    private final NoticeChatRoomParticipantStateRepository participantStateRepository;
    private final PetNoticeRepository petNoticeRepository;
    private final UserRepository userRepository;
    private final UserBlockRepository userBlockRepository;
    private final SimpMessagingTemplate simpMessagingTemplate;
    private final S3ImageStorageService s3ImageStorageService;
    private final NotificationService notificationService;
    private final UserPresenceService userPresenceService;

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
    public void syncNoticeRooms(UUID noticeId) {
        if (noticeId == null) {
            return;
        }
        List<RoomUpdatePayload> roomUpdates = noticeChatRoomRepository.findByNoticeId(noticeId).stream()
                .map(this::buildRoomUpdatePayload)
                .toList();
        afterCommitOrNow(() -> roomUpdates.forEach(this::broadcastRoomUpdate));
    }

    @Transactional
    public void deleteRoomsByNotice(PetNotice notice) {
        if (notice == null || notice.getId() == null) {
            return;
        }
        List<NoticeChatRoom> rooms = noticeChatRoomRepository.findByNotice(notice);
        if (rooms.isEmpty()) {
            return;
        }

        List<RoomDeletionPayload> deletionPayloads = rooms.stream()
                .map(room -> new RoomDeletionPayload(room.getId(), room.getOwnerUser().getId(), room.getGuestUser().getId()))
                .toList();

        participantStateRepository.deleteByRoomIn(rooms);
        noticeChatReadReceiptRepository.deleteByRoomIn(rooms);
        noticeChatMessageImageRepository.deleteByMessageRoomIn(rooms);
        noticeChatMessageRepository.deleteByRoomIn(rooms);
        noticeChatRoomRepository.deleteAll(rooms);

        afterCommitOrNow(() -> deletionPayloads.forEach(this::broadcastRoomDeletion));
    }

    @Transactional
    public NoticeChatRoomResponse getRoom(String roomId) {
        User currentUser = getCurrentUser();
        assertChatAvailableUser(currentUser, "채팅방 상세를 조회할 수 없습니다.");
        return toRoomResponse(getAccessibleRoom(roomId, currentUser), currentUser);
    }

    @Transactional
    public NoticeChatMessagePageResponse getMessages(String roomId, Long beforeSequence, Integer limit) {
        User currentUser = getCurrentUser();
        assertChatAvailableUser(currentUser, "채팅 메시지를 조회할 수 없습니다.");
        NoticeChatRoom room = getAccessibleRoom(roomId, currentUser);
        int resolvedLimit = resolveMessagePageLimit(limit);
        List<NoticeChatMessage> page = noticeChatMessageRepository.findVisiblePage(
                room,
                beforeSequence,
                PageRequest.of(0, resolvedLimit + 1)
        );
        boolean hasMore = page.size() > resolvedLimit;
        List<NoticeChatMessage> messages = page.stream().limit(resolvedLimit).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        Collections.reverse(messages);

        markRoomAsReadByWatermark(room, currentUser, latestOpponentMessage(messages, currentUser));
        Long nextBeforeSequence = hasMore && !messages.isEmpty() ? messages.get(0).getRoomSequence() : null;
        return new NoticeChatMessagePageResponse(
                messages.stream().map(message -> toMessageResponse(message, currentUser)).toList(),
                hasMore,
                nextBeforeSequence,
                resolvedLimit
        );
    }

    @Transactional
    public NoticeChatRoomResponse markRoomAsRead(String roomId, NoticeChatReadRequest request) {
        User currentUser = getCurrentUser();
        assertChatAvailableUser(currentUser, "채팅방 읽음 처리를 할 수 없습니다.");
        NoticeChatRoom room = getAccessibleRoom(roomId, currentUser);
        NoticeChatMessage latestReadMessage = resolveReadMessage(room, request);
        markRoomAsReadByWatermark(room, currentUser, latestReadMessage, request != null ? request.getLastReadRoomSequence() : null);
        return toRoomResponse(room, currentUser);
    }

    @Transactional
    public Map<String, Object> markRoomAsRead(Principal principal, NoticeChatReadRequest request) {
        User user = getEventUser(principal, "채팅방 읽음 처리를 할 수 없습니다.");
        if (request == null || request.getRoomId() == null) {
            throw ApiException.badRequest("MISSING_ROOM_ID", "채팅방 ID는 필수입니다.");
        }
        NoticeChatRoom room = getAccessibleRoom(request.getRoomId().toString(), user);
        NoticeChatMessage latestReadMessage = resolveReadMessage(room, request);
        NoticeChatRoomParticipantState state = markRoomAsReadByWatermark(room, user, latestReadMessage, request.getLastReadRoomSequence());
        return readPayload(room.getId(), user.getId(), state);
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
        return noticeChatMessageRepository.searchVisibleText(room, normalizedKeyword).stream()
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
                NoticeChatMessageResponse existingPayload = toMessageResponse(existing, sender);
                sendMessageAck(sender.getId(), room.getId(), existingPayload, true);
                return existingPayload;
            }
        }
        String content = trimToNull(request.getMessage());
        log.debug("채팅 메시지 전송 시작 roomId={} senderUserId={} messageLength={}",
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

    @Transactional(readOnly = true)
    public void publishMessageNack(String firebaseUid, java.util.UUID roomId, String clientMessageId, String errorCode, String message) {
        if (firebaseUid == null || firebaseUid.isBlank() || roomId == null) {
            return;
        }
        userRepository.findByFirebaseUid(firebaseUid).ifPresent(user -> {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "MESSAGE_NACK");
            payload.put("roomId", roomId);
            payload.put("clientMessageId", trimToNull(clientMessageId));
            payload.put("errorCode", trimToNull(errorCode) != null ? errorCode : "MESSAGE_SEND_FAILED");
            payload.put("message", trimToNull(message) != null ? message : "메시지 전송에 실패했습니다.");
            simpMessagingTemplate.convertAndSend(userRoomTopic(user.getId(), roomId), (Object) payload);
        });
    }

    @Transactional
    public NoticeChatMessageResponse sendImages(String roomId, String replyToMessageId, String message, List<MultipartFile> images) {
        User sender = getCurrentUser();
        assertChatAvailableUser(sender, "미디어 메시지를 전송할 수 없습니다.");
        NoticeChatRoom room = getAccessibleRoom(roomId, sender);
        assertRoomAcceptsConversation(room);
        User opponent = getOpponent(room, sender);
        assertOpponentCanReceiveChat(opponent);
        assertNotBlockedEitherDirection(sender, opponent);

        List<MultipartFile> nonEmptyImages = images == null ? List.of() : images.stream()
                .filter(file -> file != null && !file.isEmpty())
                .toList();
        if (nonEmptyImages.isEmpty()) {
            throw ApiException.badRequest("EMPTY_MEDIA_MESSAGE", "첨부 파일은 최소 1개 이상 필요합니다.");
        }
        if (nonEmptyImages.size() > MAX_IMAGE_COUNT) {
            throw ApiException.badRequest("TOO_MANY_MEDIA", "첨부 파일은 최대 10개까지 전송할 수 있습니다.");
        }
        validateTotalMediaSize(nonEmptyImages);
        String content = trimToNull(message);
        if (content != null && content.length() > 2000) {
            throw ApiException.badRequest("MESSAGE_TOO_LONG", "message는 2000자를 초과할 수 없습니다.");
        }

        NoticeChatMessage replyToMessage = resolveReplyTarget(parseNullableUuid(replyToMessageId, "INVALID_REPLY_MESSAGE_ID", "올바르지 않은 답장 메시지 ID 형식입니다."), room);
        NoticeChatMessageType mediaType = nonEmptyImages.stream().anyMatch(s3ImageStorageService::isVideoFile)
                ? NoticeChatMessageType.VIDEO
                : NoticeChatMessageType.IMAGE;
        List<StoredImageVariant> imageVariants = s3ImageStorageService.storeImageVariants("notice-chat", "messages", sender.getId(), nonEmptyImages);
        NoticeChatMessage saved = saveMessage(
                room,
                sender,
                mediaType,
                content,
                replyToMessage,
                null,
                imageVariants
        );
        return broadcastMessage(room, sender, saved);
    }

    private void validateTotalMediaSize(List<MultipartFile> files) {
        long totalSize = files.stream()
                .filter(file -> file != null && !file.isEmpty())
                .mapToLong(MultipartFile::getSize)
                .sum();
        if (totalSize > MAX_TOTAL_MEDIA_SIZE) {
            throw ApiException.badRequest("TOTAL_MEDIA_SIZE_EXCEEDED", "첨부 파일 총 용량은 30MB 이하여야 합니다.");
        }
    }

    @Transactional
    public NoticeChatMessageResponse updateMessage(String roomId, String messageId, NoticeChatMessageUpdateRequest request) {
        User currentUser = getCurrentUser();
        assertChatAvailableUser(currentUser, "메시지를 수정할 수 없습니다.");
        NoticeChatRoom room = getAccessibleRoom(roomId, currentUser);
        assertRoomAcceptsConversation(room);
        User opponent = getOpponent(room, currentUser);
        assertOpponentCanReceiveChat(opponent);
        assertNotBlockedEitherDirection(currentUser, opponent);

        NoticeChatMessage message = getMessageInRoom(messageId, room);
        assertOwnMessage(message, currentUser, "CHAT_MESSAGE_FORBIDDEN", "본인이 보낸 메시지만 수정할 수 있습니다.");
        if (message.getDeletedAt() != null) {
            throw ApiException.notFound("CHAT_MESSAGE_NOT_FOUND", "메시지를 찾을 수 없습니다.");
        }
        if (resolveMessageType(message) != NoticeChatMessageType.TEXT) {
            throw ApiException.badRequest("CHAT_MESSAGE_NOT_EDITABLE", "텍스트 메시지만 수정할 수 있습니다.");
        }
        String content = trimToNull(request != null ? request.getMessage() : null);
        if (content == null) {
            throw ApiException.badRequest("EMPTY_MESSAGE", "메시지 내용은 비어 있을 수 없습니다.");
        }
        message.setMessage(content);
        message.setEditedAt(Instant.now());
        NoticeChatMessage saved = noticeChatMessageRepository.saveAndFlush(message);
        refreshLastMessageSummary(room);
        NoticeChatMessageResponse currentPayload = toMessageResponse(saved, currentUser);
        NoticeChatMessageResponse opponentPayload = toMessageResponse(saved, opponent);
        RoomUpdatePayload roomUpdatePayload = buildRoomUpdatePayload(room);
        afterCommitOrNow(() -> {
            sendMessageEvent(currentUser.getId(), room.getId(), "MESSAGE_UPDATED", currentPayload);
            sendMessageEvent(opponent.getId(), room.getId(), "MESSAGE_UPDATED", opponentPayload);
            broadcastRoomUpdate(roomUpdatePayload);
        });
        return currentPayload;
    }

    @Transactional
    public void deleteMessage(String roomId, String messageId) {
        User currentUser = getCurrentUser();
        assertChatAvailableUser(currentUser, "메시지를 삭제할 수 없습니다.");
        NoticeChatRoom room = getAccessibleRoom(roomId, currentUser);
        assertRoomAcceptsConversation(room);
        User opponent = getOpponent(room, currentUser);
        assertOpponentCanReceiveChat(opponent);
        assertNotBlockedEitherDirection(currentUser, opponent);

        NoticeChatMessage message = getMessageInRoom(messageId, room);
        assertOwnMessage(message, currentUser, "CHAT_MESSAGE_FORBIDDEN", "본인이 보낸 메시지만 삭제할 수 있습니다.");
        if (message.getDeletedAt() == null) {
            message.setDeletedAt(Instant.now());
            noticeChatMessageRepository.saveAndFlush(message);
        }
        refreshLastMessageSummary(room);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "MESSAGE_DELETED");
        payload.put("roomId", room.getId());
        payload.put("messageId", message.getId());
        RoomUpdatePayload roomUpdatePayload = buildRoomUpdatePayload(room);
        afterCommitOrNow(() -> {
            simpMessagingTemplate.convertAndSend(userRoomTopic(currentUser.getId(), room.getId()), (Object) payload);
            simpMessagingTemplate.convertAndSend(userRoomTopic(opponent.getId(), room.getId()), (Object) payload);
            broadcastRoomUpdate(roomUpdatePayload);
        });
    }

    @Transactional
    public NoticeChatRoomResponse enterRoom(Principal principal, NoticeChatRoomEventRequest request) {
        User user = getEventUser(principal, "채팅방에 입장할 수 없습니다.");
        NoticeChatRoom room = getAccessibleRoom(request.getRoomId().toString(), user);
        NoticeChatRoomParticipantState state = ensureParticipantState(room, user);
        participantStateRepository.save(state);
        markRoomAsReadByWatermark(room, user, latestVisibleOpponentMessage(room, user));
        RoomUpdatePayload roomUpdatePayload = buildRoomUpdatePayload(room);
        afterCommitOrNow(() -> {
            sendRoomLifecycleEvent(room, user, "ROOM_ENTERED");
            broadcastRoomUpdate(roomUpdatePayload);
        });
        return toRoomResponse(room, user);
    }

    @Transactional
    public NoticeChatRoomResponse leaveSocketRoom(Principal principal, NoticeChatRoomEventRequest request) {
        User user = getEventUser(principal, "채팅방에서 퇴장할 수 없습니다.");
        NoticeChatRoom room = getAccessibleRoom(request.getRoomId().toString(), user);
        NoticeChatRoomParticipantState state = ensureParticipantState(room, user);
        participantStateRepository.save(state);
        RoomUpdatePayload roomUpdatePayload = buildRoomUpdatePayload(room);
        afterCommitOrNow(() -> {
            sendRoomLifecycleEvent(room, user, "ROOM_LEFT");
            broadcastRoomUpdate(roomUpdatePayload);
        });
        return toRoomResponse(room, user);
    }

    @Transactional
    public NoticeChatRoomResponse leaveRoom(String roomId) {
        User currentUser = getCurrentUser();
        assertChatAvailableUser(currentUser, "채팅방을 나갈 수 없습니다.");
        NoticeChatRoom room = getAccessibleRoom(roomId, currentUser);
        NoticeChatRoomParticipantState state = ensureParticipantState(room, currentUser);
        state.setLeftAt(Instant.now());
        participantStateRepository.save(state);
        RoomUpdatePayload roomUpdatePayload = buildRoomUpdatePayload(room);
        afterCommitOrNow(() -> {
            sendRoomLifecycleEvent(room, currentUser, "ROOM_LEFT");
            broadcastRoomUpdate(roomUpdatePayload);
        });
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
        if (Boolean.TRUE.equals(request.getClearCustomRoomName())) {
            state.setCustomRoomName(null);
        } else if (request.getCustomRoomName() != null) {
            String customRoomName = trimToNull(request.getCustomRoomName());
            if (customRoomName != null && customRoomName.length() > 100) {
                throw ApiException.badRequest("CUSTOM_ROOM_NAME_TOO_LONG", "채팅방 이름은 100자를 초과할 수 없습니다.");
            }
            state.setCustomRoomName(customRoomName);
        }
        if (Boolean.TRUE.equals(request.getClearCustomThumbnailUrl())) {
            state.setCustomThumbnailUrl(null);
        }
        participantStateRepository.save(state);
        return toRoomResponse(room, currentUser);
    }

    @Transactional
    public NoticeChatRoomResponse updateRoomThumbnail(String roomId, MultipartFile image) {
        User currentUser = getCurrentUser();
        assertChatAvailableUser(currentUser, "채팅방 썸네일을 변경할 수 없습니다.");
        NoticeChatRoom room = getAccessibleRoom(roomId, currentUser);
        NoticeChatRoomParticipantState state = ensureParticipantState(room, currentUser);
        if (image == null || image.isEmpty()) {
            throw ApiException.badRequest("EMPTY_ROOM_THUMBNAIL", "채팅방 썸네일 이미지는 필수입니다.");
        }
        if (s3ImageStorageService.isVideoFile(image)) {
            throw ApiException.badRequest("INVALID_ROOM_THUMBNAIL", "채팅방 썸네일에는 영상 파일을 사용할 수 없습니다.");
        }
        StoredImageVariant variant = s3ImageStorageService.storeImageVariant("notice-chat", "rooms", currentUser.getId(), image);
        state.setCustomThumbnailUrl(variant.thumbnailUrl() != null ? variant.thumbnailUrl() : variant.webpUrl());
        participantStateRepository.save(state);
        return toRoomResponse(room, currentUser);
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
        payload.put("senderNickname", displayUserName(sender));
        payload.put("isTyping", Boolean.TRUE.equals(request.getTyping()));

        simpMessagingTemplate.convertAndSend(
                userTypingTopic(opponent.getId(), room.getId()),
                (Object) payload
        );
        return payload;
    }

    @Transactional(readOnly = true)
    public void publishPresenceUpdatesByFirebaseUid(String firebaseUid) {
        if (firebaseUid == null || firebaseUid.isBlank()) {
            return;
        }
        log.debug("[presence] publish update by uid={}", firebaseUid);
        userRepository.findByFirebaseUid(firebaseUid).ifPresent(this::publishPresenceUpdates);
    }

    @Transactional(readOnly = true)
    public void publishPresenceEventsByFirebaseUid(String firebaseUid) {
        if (firebaseUid == null || firebaseUid.isBlank()) {
            return;
        }
        userRepository.findByFirebaseUid(firebaseUid).ifPresent(this::publishPresenceEvents);
    }

    @Transactional(readOnly = true)
    public void publishPresenceUpdates(User user) {
        if (user == null || user.getId() == null) {
            return;
        }
        List<RoomUpdatePayload> payloads = noticeChatRoomRepository.findVisibleRoomsForUser(user.getId()).stream()
                .map(this::buildRoomUpdatePayload)
                .toList();
        log.debug("[presence] broadcasting room updates userId={} roomCount={}", user.getId(), payloads.size());
        afterCommitOrNow(() -> {
            payloads.forEach(this::broadcastRoomUpdate);
            publishPresenceEvents(user);
        });
    }

    private void publishPresenceEvents(User user) {
        if (user == null || user.getId() == null) {
            return;
        }
        List<NoticeChatRoom> rooms = noticeChatRoomRepository.findVisibleRoomsForUser(user.getId());
        if (rooms.isEmpty()) {
            return;
        }
        UserPresenceService.PresenceSnapshot snapshot = userPresenceService.snapshot(user);
        String eventType = snapshot.availabilityStatus() == UserAvailabilityStatus.OFFLINE
                ? "USER_OFFLINE"
                : "PRESENCE_CHANGED";
        for (NoticeChatRoom room : rooms) {
            sendRoomLifecycleEvent(room, user, eventType);
        }
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
        UserPresenceService.PresenceSnapshot opponentPresence = userPresenceService.snapshot(opponent);
        String noticeThumbnailUrl = resolveNoticeThumbnailUrl(room.getNotice());
        String displayRoomName = trimToNull(currentState.getCustomRoomName()) != null
                ? currentState.getCustomRoomName().trim()
                : room.getNotice().getTitle() + " · " + displayUserName(opponent);
        String displayThumbnailUrl = trimToNull(currentState.getCustomThumbnailUrl()) != null
                ? currentState.getCustomThumbnailUrl().trim()
                : noticeThumbnailUrl;
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
                displayUserName(opponent),
                noticeChatMessageRepository.countUnreadByWatermark(room, currentUser, currentState.getLastReadRoomSequence()),
                currentState.getNotificationEnabled(),
                currentState.getFavorite(),
                currentState.getPinned(),
                currentState.getLeftAt(),
                opponentPresence.online(),
                opponentPresence.manualPresenceStatus().name(),
                opponentPresence.actualConnectionState(),
                opponentPresence.availabilityStatus().name(),
                opponentPresence.availabilityStatus().name(),
                opponentPresence.lastActiveAt(),
                currentState.getLastReadMessage() != null ? currentState.getLastReadMessage().getId() : null,
                currentState.getLastReadAt(),
                currentState.getLastReadRoomSequence(),
                room.getLastMessageSequence(),
                noticeThumbnailUrl,
                displayRoomName,
                displayThumbnailUrl,
                currentState.getCustomRoomName(),
                currentState.getCustomThumbnailUrl()
        );
    }

    private NoticeChatMessageResponse toMessageResponse(NoticeChatMessage message, User currentUser) {
        NoticeChatMessageType messageType = resolveMessageType(message);
        boolean mine = message.getSenderUser().getId().equals(currentUser.getId());
        Boolean isRead = mine
                ? ensureParticipantState(message.getRoom(), getOpponent(message.getRoom(), currentUser)).getLastReadRoomSequence() >= safeSequence(message)
                : Boolean.TRUE;
        return new NoticeChatMessageResponse(
                message.getId(),
                message.getRoom().getId(),
                message.getSenderUser().getId(),
                displayUserName(message.getSenderUser()),
                message.getMessage(),
                messageType.name(),
                message.getImages().stream()
                        .map(image -> new NoticeChatMessageImageResponse(
                                image.getId(),
                                image.getImageUrl(),
                                image.getOriginalUrl() != null ? image.getOriginalUrl() : image.getImageUrl(),
                                image.getWebpUrl() != null ? image.getWebpUrl() : image.getImageUrl(),
                                image.getMediumUrl(),
                                image.getThumbnailUrl(),
                                image.getPreviewUrl(),
                                image.getDisplayOrder()
                        ))
                        .toList(),
                toReplyResponse(message.getReplyToMessage()),
                isRead,
                mine,
                message.getCreatedAt(),
                message.getClientMessageId(),
                message.getRoomSequence(),
                message.getCreatedAt(),
                message.getEditedAt(),
                message.getDeletedAt(),
                message.getDeletedAt() != null
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
        if (lastMessageType == NoticeChatMessageType.VIDEO) {
            return VIDEO_MESSAGE_PREVIEW;
        }
        return noticeChatMessageRepository.findTopByRoomAndDeletedAtIsNullOrderByRoomSequenceDescCreatedAtDesc(room)
                .map(this::toPreview)
                .orElse(null);
    }

    private NoticeChatMessageType resolveLastMessageType(NoticeChatRoom room) {
        if (room.getLastMessageType() != null) {
            return room.getLastMessageType();
        }
        return noticeChatMessageRepository.findTopByRoomAndDeletedAtIsNullOrderByRoomSequenceDescCreatedAtDesc(room)
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

    private void sendRoomLifecycleEvent(NoticeChatRoom room, User user, String type) {
        User opponent = getOpponent(room, user);
        UserPresenceService.PresenceSnapshot snapshot = userPresenceService.snapshot(user);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", type);
        payload.put("roomId", room.getId());
        payload.put("userId", user.getId());
        payload.put("nickname", displayUserName(user));
        payload.put("lastActiveAt", snapshot.lastActiveAt() != null ? snapshot.lastActiveAt() : Instant.now());
        payload.put("manualPresenceStatus", snapshot.manualPresenceStatus().name());
        payload.put("connectionState", snapshot.actualConnectionState());
        payload.put("effectivePresenceStatus", snapshot.availabilityStatus().name());
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

    private void broadcastRoomDeletion(RoomDeletionPayload roomDeletionPayload) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "ROOM_DELETED");
        payload.put("roomId", roomDeletionPayload.roomId());
        simpMessagingTemplate.convertAndSend(userRoomsTopic(roomDeletionPayload.ownerUserId()), (Object) payload);
        simpMessagingTemplate.convertAndSend(userRoomsTopic(roomDeletionPayload.guestUserId()), (Object) payload);
    }

    private void sendMessageEvent(UUID userId, UUID roomId, String type, NoticeChatMessageResponse message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", type);
        payload.put("roomId", roomId);
        payload.put("message", message);
        simpMessagingTemplate.convertAndSend(userRoomTopic(userId, roomId), (Object) payload);
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
        NoticeChatRoom lockedRoom = noticeChatRoomRepository.findByIdForUpdate(room.getId())
                .orElseThrow(() -> ApiException.notFound("CHAT_ROOM_NOT_FOUND", "채팅방을 찾을 수 없습니다."));
        long nextSequence = (lockedRoom.getLastMessageSequence() == null ? 0L : lockedRoom.getLastMessageSequence()) + 1;
        List<NoticeChatMessageImage> images = new ArrayList<>();
        NoticeChatMessage message = NoticeChatMessage.builder()
                .room(lockedRoom)
                .senderUser(sender)
                .messageType(messageType)
                .message(content)
                .replyToMessage(replyToMessage)
                .clientMessageId(clientMessageId)
                .roomSequence(nextSequence)
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
        lockedRoom.setLastMessageSequence(nextSequence);
        lockedRoom.setLastMessageAt(saved.getCreatedAt() != null ? saved.getCreatedAt() : Instant.now());
        lockedRoom.setLastMessageType(saved.getMessageType());
        lockedRoom.setLastMessagePreview(toPreview(saved));
        noticeChatRoomRepository.save(lockedRoom);
        log.debug("채팅 메시지 저장 완료 roomId={} messageId={} senderUserId={} type={} createdAt={}",
                lockedRoom.getId(), saved.getId(), sender.getId(), saved.getMessageType(), saved.getCreatedAt());
        return saved;
    }

    private NoticeChatRoomParticipantState markRoomAsReadByWatermark(NoticeChatRoom room, User reader, NoticeChatMessage latestReadMessage) {
        return markRoomAsReadByWatermark(room, reader, latestReadMessage, null);
    }

    private NoticeChatRoomParticipantState markRoomAsReadByWatermark(
            NoticeChatRoom room,
            User reader,
            NoticeChatMessage latestReadMessage,
            Long requestedSequence
    ) {
        NoticeChatRoomParticipantState state = ensureParticipantState(room, reader);
        notificationService.markDirectMessageNotificationsAsRead(reader, room.getId());
        long currentSequence = state.getLastReadRoomSequence() == null ? 0L : state.getLastReadRoomSequence();
        long messageSequence = latestReadMessage != null ? safeSequence(latestReadMessage) : 0L;
        long requested = requestedSequence == null ? 0L : requestedSequence;
        long nextSequence = Math.max(currentSequence, Math.max(messageSequence, requested));
        if (nextSequence <= currentSequence && latestReadMessage == null) {
            return state;
        }
        Instant now = Instant.now();
        state.setLastReadRoomSequence(nextSequence);
        if (latestReadMessage != null && safeSequence(latestReadMessage) >= currentSequence) {
            state.setLastReadMessage(latestReadMessage);
        }
        state.setLastReadAt(now);
        participantStateRepository.save(state);

        NoticeChatReadReceipt receipt = noticeChatReadReceiptRepository.findByRoomAndReader(room, reader)
                .orElseGet(() -> NoticeChatReadReceipt.builder()
                        .room(room)
                        .reader(reader)
                        .build());
        receipt.setLastReadRoomSequence(nextSequence);
        if (latestReadMessage != null) {
            receipt.setLastReadMessage(latestReadMessage);
        }
        receipt.setReadAt(now);
        noticeChatReadReceiptRepository.save(receipt);

        Map<String, Object> payload = readPayload(room.getId(), reader.getId(), state);
        afterCommitOrNow(() -> simpMessagingTemplate.convertAndSend(userRoomTopic(getOpponent(room, reader).getId(), room.getId()), (Object) payload));
        return state;
    }

    private NoticeChatMessage getMessageInRoom(String messageId, NoticeChatRoom room) {
        try {
            NoticeChatMessage message = noticeChatMessageRepository.findById(UUID.fromString(messageId))
                    .orElseThrow(() -> ApiException.notFound("CHAT_MESSAGE_NOT_FOUND", "메시지를 찾을 수 없습니다."));
            if (!message.getRoom().getId().equals(room.getId())) {
                throw ApiException.notFound("CHAT_MESSAGE_NOT_FOUND", "메시지를 찾을 수 없습니다.");
            }
            return message;
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("INVALID_CHAT_MESSAGE_ID", "올바르지 않은 메시지 ID 형식입니다.");
        }
    }

    private void assertOwnMessage(NoticeChatMessage message, User currentUser, String code, String fallbackMessage) {
        if (!message.getSenderUser().getId().equals(currentUser.getId())) {
            throw ApiException.forbidden(code, fallbackMessage);
        }
    }

    private void refreshLastMessageSummary(NoticeChatRoom room) {
        NoticeChatMessage latest = noticeChatMessageRepository.findTopByRoomAndDeletedAtIsNullOrderByRoomSequenceDescCreatedAtDesc(room)
                .orElse(null);
        if (latest == null) {
            room.setLastMessageAt(null);
            room.setLastMessageType(null);
            room.setLastMessagePreview(null);
        } else {
            room.setLastMessageAt(latest.getCreatedAt());
            room.setLastMessageType(resolveMessageType(latest));
            room.setLastMessagePreview(toPreview(latest));
        }
        noticeChatRoomRepository.save(room);
    }

    private void ensureParticipantStates(NoticeChatRoom room) {
        ensureParticipantState(room, room.getOwnerUser());
        ensureParticipantState(room, room.getGuestUser());
    }

    private int resolveMessagePageLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_MESSAGE_PAGE_LIMIT;
        }
        return Math.min(limit, MAX_MESSAGE_PAGE_LIMIT);
    }

    private NoticeChatMessage latestOpponentMessage(List<NoticeChatMessage> messages, User reader) {
        return messages.stream()
                .filter(message -> !message.getSenderUser().getId().equals(reader.getId()))
                .max(Comparator.comparingLong(this::safeSequence))
                .orElse(null);
    }

    private NoticeChatMessage latestVisibleOpponentMessage(NoticeChatRoom room, User reader) {
        return noticeChatMessageRepository.findVisiblePage(room, null, PageRequest.of(0, 100))
                .stream()
                .filter(message -> !message.getSenderUser().getId().equals(reader.getId()))
                .findFirst()
                .orElse(null);
    }

    private NoticeChatMessage resolveReadMessage(NoticeChatRoom room, NoticeChatReadRequest request) {
        if (request == null || request.getLastReadMessageId() == null) {
            return null;
        }
        NoticeChatMessage message = noticeChatMessageRepository.findById(request.getLastReadMessageId())
                .orElseThrow(() -> ApiException.notFound("CHAT_MESSAGE_NOT_FOUND", "읽음 처리할 메시지를 찾을 수 없습니다."));
        if (!message.getRoom().getId().equals(room.getId()) || message.getDeletedAt() != null) {
            throw ApiException.badRequest("INVALID_READ_MESSAGE", "해당 채팅방의 메시지만 읽음 처리할 수 있습니다.");
        }
        return message;
    }

    private Map<String, Object> readPayload(UUID roomId, UUID readerUserId, NoticeChatRoomParticipantState state) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "READ_RECEIPT");
        payload.put("roomId", roomId);
        payload.put("readerUserId", readerUserId);
        payload.put("latestReadMessageId", state.getLastReadMessage() != null ? state.getLastReadMessage().getId() : null);
        payload.put("latestReadRoomSequence", state.getLastReadRoomSequence());
        payload.put("readAt", state.getLastReadAt());
        return payload;
    }

    private long safeSequence(NoticeChatMessage message) {
        return message.getRoomSequence() == null ? 0L : message.getRoomSequence();
    }

    private String resolveNoticeThumbnailUrl(PetNotice notice) {
        if (notice.getImages() == null || notice.getImages().isEmpty()) {
            return null;
        }
        return notice.getImages().get(0).getImageUrl();
    }

    private NoticeChatRoomParticipantState ensureParticipantState(NoticeChatRoom room, User user) {
        return participantStateRepository.findByRoomAndUser(room, user)
                .orElseGet(() -> participantStateRepository.save(NoticeChatRoomParticipantState.builder()
                        .room(room)
                        .user(user)
                .notificationEnabled(true)
                .favorite(false)
                .pinned(false)
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
        NoticeChatRoom savedRoom = saved.getRoom();
        User opponent = getOpponent(savedRoom, sender);
        NoticeChatMessageResponse senderPayload = toMessageResponse(saved, sender);
        NoticeChatMessageResponse opponentPayload = toMessageResponse(saved, opponent);
        RoomUpdatePayload roomUpdatePayload = buildRoomUpdatePayload(savedRoom);
        boolean opponentNotificationEnabled = Boolean.TRUE.equals(ensureParticipantState(savedRoom, opponent).getNotificationEnabled());
        notifyDirectMessage(savedRoom, saved, sender, opponent, opponentNotificationEnabled);

        afterCommitOrNow(() -> {
            sendMessageAck(sender.getId(), savedRoom.getId(), senderPayload, false);
            simpMessagingTemplate.convertAndSend(userRoomTopic(sender.getId(), savedRoom.getId()), senderPayload);
            simpMessagingTemplate.convertAndSend(userRoomTopic(opponent.getId(), savedRoom.getId()), opponentPayload);
            broadcastRoomUpdate(roomUpdatePayload);
        });
        return senderPayload;
    }

    private void sendMessageAck(java.util.UUID senderUserId, java.util.UUID roomId, NoticeChatMessageResponse senderPayload, boolean duplicate) {
        if (senderUserId == null || roomId == null || senderPayload == null || trimToNull(senderPayload.clientMessageId()) == null) {
            return;
        }
        Map<String, Object> ackPayload = new LinkedHashMap<>();
        ackPayload.put("type", "MESSAGE_ACK");
        ackPayload.put("roomId", roomId);
        ackPayload.put("messageId", senderPayload.id());
        ackPayload.put("clientMessageId", senderPayload.clientMessageId());
        ackPayload.put("roomSequence", senderPayload.roomSequence());
        ackPayload.put("serverReceivedAt", senderPayload.serverReceivedAt());
        ackPayload.put("duplicate", duplicate);
        simpMessagingTemplate.convertAndSend(userRoomTopic(senderUserId, roomId), (Object) ackPayload);
    }

    private void notifyDirectMessage(NoticeChatRoom room, NoticeChatMessage message, User sender, User opponent, boolean opponentNotificationEnabled) {
        if (!opponentNotificationEnabled) {
            return;
        }
        boolean replyToOpponent = message.getReplyToMessage() != null
                && message.getReplyToMessage().getSenderUser() != null
                && message.getReplyToMessage().getSenderUser().getId().equals(opponent.getId());
        NotificationType type = replyToOpponent ? NotificationType.DM_REPLY : NotificationType.DM_MESSAGE;
        String preview = toPreview(message);
        if (preview == null) {
            preview = "새 메시지가 도착했습니다.";
        }
        notificationService.createAndSendNotification(
                opponent,
                sender,
                type,
                NotificationTargetType.NOTICE_CHAT_MESSAGE,
                message.getId(),
                replyToOpponent ? "답장이 도착했습니다." : sender.getNickname() + "님의 메시지",
                preview,
                NotificationPriority.HIGH,
                "dm-message:" + opponent.getId() + ":" + message.getId(),
                Map.of(
                        "roomId", room.getId().toString(),
                        "roomName", resolveNotificationRoomName(room, sender),
                        "messageId", message.getId().toString(),
                        "senderUserId", sender.getId().toString()
                )
        );
    }

    private String resolveNotificationRoomName(NoticeChatRoom room, User sender) {
        String senderName = displayUserName(sender);
        String noticeTitle = room != null && room.getNotice() != null ? trimToNull(room.getNotice().getTitle()) : null;
        if (noticeTitle == null) {
            return senderName;
        }
        return senderName + " · " + noticeTitle;
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
        NoticeChatMessageType type = resolveMessageType(replyToMessage);
        String preview = type == NoticeChatMessageType.IMAGE ? "사진"
                : type == NoticeChatMessageType.VIDEO ? "동영상"
                : toPreview(replyToMessage);
        String thumbnailUrl = replyToMessage.getImages().stream()
                .min(Comparator.comparingInt(NoticeChatMessageImage::getDisplayOrder))
                .map(image -> image.getThumbnailUrl() != null ? image.getThumbnailUrl()
                        : image.getPreviewUrl() != null ? image.getPreviewUrl()
                        : image.getWebpUrl() != null ? image.getWebpUrl()
                        : image.getImageUrl())
                .orElse(null);
        return new NoticeChatMessageReplyResponse(
                replyToMessage.getId(),
                replyToMessage.getSenderUser().getId(),
                displayUserName(replyToMessage.getSenderUser()),
                preview,
                type.name(),
                thumbnailUrl
        );
    }

    private String displayUserName(User user) {
        if (user == null) {
            return "알 수 없는 사용자";
        }
        if (user.getStatus() == UserStatus.WITHDRAWN) {
            return "탈퇴한 사용자";
        }
        if (user.getStatus() == UserStatus.BANNED) {
            return "이용 제한 사용자";
        }
        return user.getNickname();
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

    private record RoomDeletionPayload(
            UUID roomId,
            UUID ownerUserId,
            UUID guestUserId
    ) {
    }
}
