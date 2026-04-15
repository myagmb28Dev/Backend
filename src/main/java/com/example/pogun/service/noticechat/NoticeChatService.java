package com.example.pogun.service.noticechat;

import com.example.pogun.dto.common.ApiResponse.ApiException;
import com.example.pogun.dto.noticechat.*;
import com.example.pogun.dto.storage.StoredMediaVariant;
import com.example.pogun.entity.missingpet.PetNotice;
import com.example.pogun.entity.missingpet.PetNoticeImage;
import com.example.pogun.entity.missingpet.enums.PetNoticeStatus;
import com.example.pogun.entity.noticechat.*;
import com.example.pogun.entity.noticechat.enums.*;
import com.example.pogun.entity.user.User;
import com.example.pogun.entity.user.enums.UserStatus;
import com.example.pogun.repository.missingpet.PetNoticeRepository;
import com.example.pogun.repository.noticechat.*;
import com.example.pogun.repository.user.*;
import com.example.pogun.service.storage.LocalImageStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.time.Instant;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class NoticeChatService {
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;
    private static final int MAX_ATTACHMENTS = 10;
    private static final String IMAGE_PREVIEW = "사진을 보냈습니다";
    private static final String VIDEO_PREVIEW = "동영상을 보냈습니다";

    private final NoticeChatRoomRepository noticeChatRoomRepository;
    private final NoticeChatMessageRepository noticeChatMessageRepository;
    private final NoticeChatMessageImageRepository noticeChatMessageImageRepository;
    private final NoticeChatRoomParticipantStateRepository participantStateRepository;
    private final NoticeChatReadReceiptRepository readReceiptRepository;
    private final PetNoticeRepository petNoticeRepository;
    private final UserRepository userRepository;
    private final UserBlockRepository userBlockRepository;
    private final SimpMessagingTemplate simpMessagingTemplate;
    private final LocalImageStorageService localImageStorageService;

    @Transactional
    public NoticeChatRoomCreateResult createOrGetRoom(String noticeId) {
        User currentUser = currentUser();
        chatUser(currentUser, "채팅방을 생성할 수 없습니다.");
        PetNotice notice = notice(noticeId);
        if (Boolean.TRUE.equals(notice.getHidden())) throw ApiException.conflict("CHAT_NOTICE_HIDDEN", "숨김 처리된 공고로는 채팅을 시작할 수 없습니다.");
        if (notice.getStatus() != PetNoticeStatus.OPEN) throw ApiException.conflict("CHAT_NOTICE_CLOSED", "종료된 공고로는 새 채팅을 시작할 수 없습니다.");
        User owner = notice.getAuthor();
        if (owner.getId().equals(currentUser.getId())) throw ApiException.conflict("CHAT_ROOM_SELF_NOTICE", "본인 공고에는 직접 문의할 수 없습니다.");
        chatUser(owner, "상대방이 채팅을 받을 수 없습니다.");
        notBlocked(currentUser, owner);
        return noticeChatRoomRepository.findByNoticeAndOwnerUserAndGuestUser(notice, owner, currentUser)
                .map(room -> {
                    ensureStates(room);
                    state(room, currentUser).setLeftAt(null);
                    return new NoticeChatRoomCreateResult(false, roomResponse(room, currentUser));
                })
                .orElseGet(() -> {
                    NoticeChatRoom saved = noticeChatRoomRepository.save(NoticeChatRoom.builder()
                            .notice(notice).ownerUser(owner).guestUser(currentUser)
                            .status(NoticeChatRoomStatus.OPEN).lastMessageSequence(0L).build());
                    ensureStates(saved);
                    afterCommit(() -> broadcastRoom(saved));
                    return new NoticeChatRoomCreateResult(true, roomResponse(saved, currentUser));
                });
    }

    @Transactional
    public List<NoticeChatRoomResponse> getRooms() {
        User user = currentUser();
        return participantStateRepository.findByUserAndLeftAtIsNull(user).stream()
                .map(NoticeChatRoomParticipantState::getRoom)
                .filter(room -> room.getNotice() != null)
                .sorted(roomComparator(user))
                .map(room -> roomResponse(room, user))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<NoticeChatRoomResponse> searchRooms(String keyword) {
        String key = trim(keyword);
        if (key == null) return getRooms();
        String lower = key.toLowerCase();
        return getRooms().stream()
                .filter(room -> contains(room.noticeTitle(), lower) || contains(room.opponentNickname(), lower))
                .toList();
    }

    @Transactional
    public NoticeChatRoomResponse getRoom(String roomId) {
        User user = currentUser();
        return roomResponse(accessibleRoom(roomId, user), user);
    }

    @Transactional
    public NoticeChatMessagePageResponse getMessages(String roomId, Long beforeSequence, Integer requestedLimit) {
        User user = currentUser();
        NoticeChatRoom room = accessibleRoom(roomId, user);
        int limit = Math.min(requestedLimit == null || requestedLimit <= 0 ? DEFAULT_LIMIT : requestedLimit, MAX_LIMIT);
        List<NoticeChatMessage> desc = noticeChatMessageRepository.findVisiblePage(room, beforeSequence, PageRequest.of(0, limit + 1));
        boolean hasMore = desc.size() > limit;
        List<NoticeChatMessage> page = new ArrayList<>(hasMore ? desc.subList(0, limit) : desc);
        page.sort(Comparator.comparing(m -> seq(m.getRoomSequence())));
        page.stream().filter(m -> !m.getSenderUser().getId().equals(user.getId())).reduce((a, b) -> b)
                .ifPresent(latest -> readTo(room, user, latest, latest.getRoomSequence(), true));
        Long next = hasMore && !page.isEmpty() ? page.get(0).getRoomSequence() : null;
        return new NoticeChatMessagePageResponse(page.stream().map(m -> messageResponse(m, user)).toList(), hasMore, next, limit);
    }

    @Transactional
    public NoticeChatRoomResponse markRoomAsRead(String roomId) {
        User user = currentUser();
        NoticeChatRoom room = accessibleRoom(roomId, user);
        NoticeChatMessage latest = noticeChatMessageRepository.findTopByRoomAndDeletedAtIsNullOrderByRoomSequenceDescCreatedAtDesc(room).orElse(null);
        readTo(room, user, latest, latest == null ? 0L : latest.getRoomSequence(), true);
        return roomResponse(room, user);
    }

    @Transactional
    public Map<String, Object> markRoomAsRead(Principal principal, NoticeChatReadRequest request) {
        User user = eventUser(principal, "읽음 처리를 할 수 없습니다.");
        NoticeChatRoom room = accessibleRoom(request.getRoomId().toString(), user);
        NoticeChatMessage latest = request.getLastReadMessageId() == null ? null : noticeChatMessageRepository.findById(request.getLastReadMessageId()).orElse(null);
        Long sequence = request.getLastReadRoomSequence() != null ? request.getLastReadRoomSequence() : latest == null ? 0L : latest.getRoomSequence();
        readTo(room, user, latest, sequence, true);
        return readPayload(room, user, latest, sequence);
    }

    @Transactional(readOnly = true)
    public List<NoticeChatMessageResponse> searchMessages(String roomId, String keyword) {
        User user = currentUser();
        NoticeChatRoom room = accessibleRoom(roomId, user);
        String key = trim(keyword);
        if (key == null) return List.of();
        return noticeChatMessageRepository.searchVisibleText(room, key).stream().map(m -> messageResponse(m, user)).toList();
    }

    @Transactional
    public NoticeChatMessageResponse sendMessage(Principal principal, NoticeChatMessageRequest request) {
        User sender = eventUser(principal, "메시지를 전송할 수 없습니다.");
        NoticeChatRoom room = accessibleRoom(request.getRoomId().toString(), sender);
        accepts(room);
        User opponent = opponent(room, sender);
        chatUser(opponent, "상대방이 채팅을 받을 수 없습니다.");
        notBlocked(sender, opponent);
        String clientId = trim(request.getClientMessageId());
        if (clientId != null) {
            NoticeChatMessage existing = noticeChatMessageRepository.findByRoomAndSenderUserAndClientMessageId(room, sender, clientId).orElse(null);
            if (existing != null) return messageResponse(existing, sender);
        }
        String content = trim(request.getMessage());
        if (content == null) throw ApiException.badRequest("EMPTY_MESSAGE", "메시지 내용은 비어 있을 수 없습니다.");
        NoticeChatMessage saved = save(room, sender, NoticeChatMessageType.TEXT, content, replyTarget(request.getReplyToMessageId(), room), clientId, List.of());
        return broadcastMessage(room, sender, saved);
    }

    @Transactional
    public NoticeChatMessageResponse sendImages(String roomId, String replyToMessageId, String message, List<MultipartFile> files) {
        User sender = currentUser();
        NoticeChatRoom room = accessibleRoom(roomId, sender);
        accepts(room);
        User opponent = opponent(room, sender);
        chatUser(opponent, "상대방이 채팅을 받을 수 없습니다.");
        notBlocked(sender, opponent);
        List<MultipartFile> attachments = files == null ? List.of() : files.stream().filter(f -> f != null && !f.isEmpty()).toList();
        if (attachments.isEmpty()) throw ApiException.badRequest("EMPTY_IMAGE_MESSAGE", "첨부 파일은 최소 1개 이상 필요합니다.");
        if (attachments.size() > MAX_ATTACHMENTS) throw ApiException.badRequest("TOO_MANY_ATTACHMENTS", "첨부 파일은 최대 10개까지 가능합니다.");
        String content = trim(message);
        if (content != null && content.length() > 2000) throw ApiException.badRequest("MESSAGE_TOO_LONG", "message는 2000자를 초과할 수 없습니다.");
        List<StoredMediaVariant> variants = localImageStorageService.storeChatMedia("notice-chat", "messages", sender.getId(), attachments);
        NoticeChatMessageType type = variants.stream().anyMatch(StoredMediaVariant::video) ? NoticeChatMessageType.VIDEO : NoticeChatMessageType.IMAGE;
        NoticeChatMessage saved = save(room, sender, type, content, replyTarget(uuidOrNull(replyToMessageId), room), null, variants);
        return broadcastMessage(room, sender, saved);
    }

    @Transactional
    public NoticeChatRoomResponse enterRoom(Principal principal, NoticeChatRoomEventRequest request) {
        User user = eventUser(principal, "채팅방에 입장할 수 없습니다.");
        NoticeChatRoom room = accessibleRoom(request.getRoomId().toString(), user);
        NoticeChatRoomParticipantState state = state(room, user);
        state.setOnline(true);
        state.setLastActiveAt(Instant.now());
        NoticeChatMessage latest = noticeChatMessageRepository.findTopByRoomAndDeletedAtIsNullOrderByRoomSequenceDescCreatedAtDesc(room).orElse(null);
        readTo(room, user, latest, latest == null ? 0L : latest.getRoomSequence(), true);
        lifecycle(room, user, "ROOM_ENTERED");
        return roomResponse(room, user);
    }

    @Transactional
    public NoticeChatRoomResponse leaveSocketRoom(Principal principal, NoticeChatRoomEventRequest request) {
        User user = eventUser(principal, "채팅방에서 퇴장할 수 없습니다.");
        NoticeChatRoom room = accessibleRoom(request.getRoomId().toString(), user);
        NoticeChatRoomParticipantState state = state(room, user);
        state.setOnline(false);
        state.setLastActiveAt(Instant.now());
        lifecycle(room, user, "ROOM_LEFT");
        return roomResponse(room, user);
    }

    @Transactional
    public NoticeChatRoomResponse leaveRoom(String roomId) {
        User user = currentUser();
        NoticeChatRoom room = accessibleRoom(roomId, user);
        NoticeChatRoomParticipantState state = state(room, user);
        state.setLeftAt(Instant.now());
        state.setOnline(false);
        state.setLastActiveAt(state.getLeftAt());
        lifecycle(room, user, "ROOM_LEFT");
        return roomResponse(room, user);
    }

    @Transactional
    public NoticeChatRoomResponse updateSettings(String roomId, NoticeChatRoomSettingsRequest request) {
        User user = currentUser();
        NoticeChatRoom room = accessibleRoom(roomId, user);
        NoticeChatRoomParticipantState state = state(room, user);
        if (request.getNotificationEnabled() != null) state.setNotificationEnabled(request.getNotificationEnabled());
        if (request.getFavorite() != null) state.setFavorite(request.getFavorite());
        if (request.getPinned() != null) state.setPinned(request.getPinned());
        return roomResponse(room, user);
    }

    @Transactional
    public void markUserOffline(String firebaseUid) {
        if (firebaseUid == null || firebaseUid.isBlank()) return;
        userRepository.findByFirebaseUid(firebaseUid).ifPresent(user -> {
            Instant now = Instant.now();
            participantStateRepository.findByUser(user).forEach(state -> {
                state.setOnline(false);
                state.setLastActiveAt(now);
            });
        });
    }

    @Transactional(readOnly = true)
    public Map<String, Object> sendTypingEvent(Principal principal, NoticeChatTypingRequest request) {
        User sender = eventUser(principal, "입력 중 상태를 전송할 수 없습니다.");
        NoticeChatRoom room = accessibleRoom(request.getRoomId().toString(), sender);
        accepts(room);
        User opponent = opponent(room, sender);
        chatUser(opponent, "상대방이 채팅을 받을 수 없습니다.");
        notBlocked(sender, opponent);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("roomId", room.getId());
        payload.put("senderUserId", sender.getId());
        payload.put("senderNickname", sender.getNickname());
        payload.put("isTyping", Boolean.TRUE.equals(request.getTyping()));
        simpMessagingTemplate.convertAndSend(userTypingTopic(opponent.getId(), room.getId()), (Object) payload);
        return payload;
    }

    private NoticeChatMessage save(NoticeChatRoom room, User sender, NoticeChatMessageType type, String content, NoticeChatMessage reply, String clientId, List<StoredMediaVariant> variants) {
        NoticeChatRoom locked = noticeChatRoomRepository.findByIdForUpdate(room.getId()).orElse(room);
        long next = seq(locked.getLastMessageSequence()) + 1;
        List<NoticeChatMessageImage> images = new ArrayList<>();
        NoticeChatMessage message = NoticeChatMessage.builder()
                .room(locked).senderUser(sender).messageType(type).message(content)
                .replyToMessage(reply).clientMessageId(clientId).roomSequence(next).images(images).build();
        for (int i = 0; i < variants.size(); i++) {
            StoredMediaVariant variant = variants.get(i);
            images.add(NoticeChatMessageImage.builder()
                    .message(message).imageUrl(variant.displayUrl()).originalUrl(variant.originalUrl())
                    .webpUrl(variant.webpUrl()).mediumUrl(variant.mediumUrl()).thumbnailUrl(variant.thumbnailUrl())
                    .previewUrl(variant.previewUrl()).contentType(variant.contentType()).displayOrder(i).build());
        }
        NoticeChatMessage saved = noticeChatMessageRepository.saveAndFlush(message);
        locked.setLastMessageSequence(next);
        locked.setLastMessageAt(saved.getCreatedAt() == null ? Instant.now() : saved.getCreatedAt());
        locked.setLastMessageType(type.name());
        locked.setLastMessagePreview(preview(saved));
        noticeChatRoomRepository.save(locked);
        return saved;
    }

    private NoticeChatMessageResponse broadcastMessage(NoticeChatRoom room, User sender, NoticeChatMessage saved) {
        User opponent = opponent(room, sender);
        NoticeChatMessageResponse senderPayload = messageResponse(saved, sender);
        NoticeChatMessageResponse opponentPayload = messageResponse(saved, opponent);
        afterCommit(() -> {
            simpMessagingTemplate.convertAndSend(userRoomTopic(sender.getId(), room.getId()), senderPayload);
            simpMessagingTemplate.convertAndSend(userRoomTopic(opponent.getId(), room.getId()), opponentPayload);
            broadcastRoom(room);
        });
        return senderPayload;
    }

    private NoticeChatRoomResponse roomResponse(NoticeChatRoom room, User currentUser) {
        User opponent = opponent(room, currentUser);
        NoticeChatRoomParticipantState current = state(room, currentUser);
        NoticeChatRoomParticipantState other = state(room, opponent);
        return new NoticeChatRoomResponse(
                room.getId(), room.getNotice().getId(), room.getNotice().getTitle(),
                room.getNotice().getImages().stream().findFirst().map(PetNoticeImage::getImageUrl).orElse(null),
                room.getStatus().name(), room.getLastMessageType(), room.getLastMessageAt(), room.getLastMessagePreview(),
                seq(room.getLastMessageSequence()), room.getCreatedAt(), opponent.getId(), opponent.getNickname(),
                noticeChatMessageRepository.countUnreadByWatermark(room, currentUser, seq(current.getLastReadRoomSequence())),
                current.getNotificationEnabled(), current.getFavorite(), current.getPinned(), current.getLeftAt(),
                other.getOnline(), other.getLastActiveAt(),
                current.getLastReadMessage() == null ? null : current.getLastReadMessage().getId(),
                current.getLastReadAt(), seq(current.getLastReadRoomSequence()));
    }

    private NoticeChatMessageResponse messageResponse(NoticeChatMessage message, User currentUser) {
        User other = opponent(message.getRoom(), currentUser);
        boolean mine = message.getSenderUser().getId().equals(currentUser.getId());
        boolean read = mine ? seq(state(message.getRoom(), other).getLastReadRoomSequence()) >= seq(message.getRoomSequence()) : true;
        return new NoticeChatMessageResponse(
                message.getId(), message.getRoom().getId(), message.getSenderUser().getId(), message.getSenderUser().getNickname(),
                message.getMessage(), message.getMessageType().name(), message.getImages().stream().map(this::imageResponse).toList(),
                replyResponse(message.getReplyToMessage()), read, mine, message.getCreatedAt(), message.getClientMessageId(),
                message.getRoomSequence(), message.getCreatedAt());
    }

    private NoticeChatMessageImageResponse imageResponse(NoticeChatMessageImage image) {
        return new NoticeChatMessageImageResponse(image.getId(), image.getImageUrl(), image.getOriginalUrl(), image.getWebpUrl(), image.getMediumUrl(), image.getThumbnailUrl(), image.getPreviewUrl(), image.getContentType(), image.getDisplayOrder());
    }

    private NoticeChatMessageReplyResponse replyResponse(NoticeChatMessage reply) {
        if (reply == null) return null;
        NoticeChatMessageImage image = reply.getImages().stream().findFirst().orElse(null);
        return new NoticeChatMessageReplyResponse(reply.getId(), reply.getSenderUser().getId(), reply.getSenderUser().getNickname(),
                reply.getMessageType() == NoticeChatMessageType.VIDEO ? "동영상" : reply.getMessageType() == NoticeChatMessageType.IMAGE ? "사진" : preview(reply),
                reply.getMessageType().name(), image == null ? null : first(image.getThumbnailUrl(), image.getPreviewUrl(), image.getWebpUrl(), image.getImageUrl()));
    }

    private void readTo(NoticeChatRoom room, User reader, NoticeChatMessage latest, Long sequence, boolean broadcast) {
        long next = Math.max(seq(sequence), latest == null ? 0L : seq(latest.getRoomSequence()));
        NoticeChatRoomParticipantState state = state(room, reader);
        if (next >= seq(state.getLastReadRoomSequence())) {
            state.setLastReadRoomSequence(next);
            state.setLastReadMessage(latest);
            state.setLastReadAt(Instant.now());
        }
        NoticeChatReadReceipt receipt = readReceiptRepository.findByRoomAndReader(room, reader)
                .orElseGet(() -> NoticeChatReadReceipt.builder().room(room).reader(reader).readAt(Instant.now()).build());
        receipt.setLastReadMessage(latest);
        receipt.setLastReadRoomSequence(next);
        receipt.setReadAt(state.getLastReadAt() == null ? Instant.now() : state.getLastReadAt());
        readReceiptRepository.save(receipt);
        if (broadcast) afterCommit(() -> simpMessagingTemplate.convertAndSend(userRoomTopic(opponent(room, reader).getId(), room.getId()), (Object) readPayload(room, reader, latest, next)));
    }

    private Map<String, Object> readPayload(NoticeChatRoom room, User reader, NoticeChatMessage latest, Long sequence) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "READ_RECEIPT");
        payload.put("roomId", room.getId());
        payload.put("readerUserId", reader.getId());
        payload.put("latestReadMessageId", latest == null ? null : latest.getId());
        payload.put("latestReadRoomSequence", seq(sequence));
        return payload;
    }

    private void ensureStates(NoticeChatRoom room) {
        state(room, room.getOwnerUser());
        state(room, room.getGuestUser());
    }

    private NoticeChatRoomParticipantState state(NoticeChatRoom room, User user) {
        return participantStateRepository.findByRoomAndUser(room, user).orElseGet(() -> participantStateRepository.save(NoticeChatRoomParticipantState.builder().room(room).user(user).build()));
    }

    private NoticeChatRoom accessibleRoom(String roomId, User user) {
        NoticeChatRoom room;
        try {
            room = noticeChatRoomRepository.findById(UUID.fromString(roomId)).orElseThrow(() -> ApiException.notFound("CHAT_ROOM_NOT_FOUND", "채팅방을 찾을 수 없습니다."));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("INVALID_CHAT_ROOM_ID", "올바르지 않은 채팅방 ID 형식입니다.");
        }
        if (!room.getOwnerUser().getId().equals(user.getId()) && !room.getGuestUser().getId().equals(user.getId())) {
            throw ApiException.forbidden("CHAT_ROOM_FORBIDDEN", "해당 채팅방에 접근할 수 없습니다.");
        }
        return room;
    }

    private NoticeChatMessage replyTarget(UUID replyId, NoticeChatRoom room) {
        if (replyId == null) return null;
        NoticeChatMessage reply = noticeChatMessageRepository.findById(replyId).orElseThrow(() -> ApiException.notFound("REPLY_MESSAGE_NOT_FOUND", "답장 대상 메시지를 찾을 수 없습니다."));
        if (!reply.getRoom().getId().equals(room.getId()) || reply.getDeletedAt() != null) throw ApiException.badRequest("INVALID_REPLY_MESSAGE", "같은 채팅방의 표시 중인 메시지에만 답장할 수 있습니다.");
        return reply;
    }

    private User currentUser() {
        return userByFirebaseUid((String) SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    }

    private User eventUser(Principal principal, String message) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) throw ApiException.unauthorized("WEBSOCKET_UNAUTHORIZED", "웹소켓 사용자 인증 정보를 찾을 수 없습니다.");
        User user = userByFirebaseUid(principal.getName());
        chatUser(user, message);
        return user;
    }

    private User userByFirebaseUid(String firebaseUid) {
        return userRepository.findByFirebaseUid(firebaseUid).orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));
    }

    private PetNotice notice(String noticeId) {
        try {
            return petNoticeRepository.findById(UUID.fromString(noticeId)).orElseThrow(() -> ApiException.notFound("NOTICE_NOT_FOUND", "실종 공고를 찾을 수 없습니다."));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("INVALID_NOTICE_ID", "올바르지 않은 공고 ID 형식입니다.");
        }
    }

    private void accepts(NoticeChatRoom room) {
        if (room.getStatus() == NoticeChatRoomStatus.CLOSED) throw ApiException.conflict("CHAT_ROOM_CLOSED", "종료된 채팅방입니다.");
        if (Boolean.TRUE.equals(room.getNotice().getHidden())) throw ApiException.conflict("CHAT_NOTICE_HIDDEN", "숨김 처리된 공고의 채팅방입니다.");
        if (room.getNotice().getStatus() != PetNoticeStatus.OPEN) throw ApiException.conflict("CHAT_NOTICE_CLOSED", "종료된 공고의 채팅방입니다.");
    }

    private void chatUser(User user, String fallback) {
        if (user.getStatus() == null || user.getStatus() == UserStatus.ACTIVE) return;
        if (user.getStatus() == UserStatus.BANNED) throw ApiException.forbidden("CHAT_USER_BANNED", "제재된 사용자는 채팅을 이용할 수 없습니다.");
        if (user.getStatus() == UserStatus.WITHDRAWN) throw ApiException.forbidden("CHAT_USER_WITHDRAWN", "탈퇴한 사용자는 채팅을 이용할 수 없습니다.");
        throw ApiException.forbidden("CHAT_USER_INACTIVE", fallback);
    }

    private void notBlocked(User a, User b) {
        if (userBlockRepository.existsByBlockerAndBlocked(a, b) || userBlockRepository.existsByBlockerAndBlocked(b, a)) throw ApiException.forbidden("CHAT_USER_BLOCKED", "차단된 사용자와는 채팅할 수 없습니다.");
    }

    private User opponent(NoticeChatRoom room, User user) {
        return room.getOwnerUser().getId().equals(user.getId()) ? room.getGuestUser() : room.getOwnerUser();
    }

    private Comparator<NoticeChatRoom> roomComparator(User user) {
        return Comparator.comparing((NoticeChatRoom room) -> Boolean.TRUE.equals(state(room, user).getPinned())).reversed()
                .thenComparing(room -> room.getLastMessageAt() == null ? room.getCreatedAt() : room.getLastMessageAt(), Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private void broadcastRoom(NoticeChatRoom room) {
        simpMessagingTemplate.convertAndSend(userRoomsTopic(room.getOwnerUser().getId()), roomResponse(room, room.getOwnerUser()));
        simpMessagingTemplate.convertAndSend(userRoomsTopic(room.getGuestUser().getId()), roomResponse(room, room.getGuestUser()));
    }

    private void lifecycle(NoticeChatRoom room, User user, String type) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", type);
        payload.put("roomId", room.getId());
        payload.put("userId", user.getId());
        payload.put("nickname", user.getNickname());
        payload.put("lastActiveAt", Instant.now());
        simpMessagingTemplate.convertAndSend(userRoomTopic(opponent(room, user).getId(), room.getId()), (Object) payload);
    }

    private String userRoomTopic(UUID userId, UUID roomId) { return "/topic/chat/users/" + userId + "/rooms/" + roomId; }
    private String userRoomsTopic(UUID userId) { return "/topic/chat/users/" + userId + "/rooms"; }
    private String userTypingTopic(UUID userId, UUID roomId) { return userRoomTopic(userId, roomId) + "/typing"; }
    private String preview(NoticeChatMessage m) {
        if (m == null) return null;
        if (m.getMessageType() == NoticeChatMessageType.VIDEO) return VIDEO_PREVIEW;
        if (m.getMessageType() == NoticeChatMessageType.IMAGE) return IMAGE_PREVIEW;
        String t = trim(m.getMessage());
        return t == null ? null : t.length() <= 80 ? t : t.substring(0, 77) + "...";
    }
    private long seq(Long value) { return value == null ? 0L : value; }
    private UUID uuidOrNull(String value) { if (value == null || value.isBlank()) return null; try { return UUID.fromString(value); } catch (IllegalArgumentException e) { throw ApiException.badRequest("INVALID_UUID", "올바르지 않은 ID 형식입니다."); } }
    private String first(String... values) { for (String v : values) if (v != null && !v.isBlank()) return v; return null; }
    private boolean contains(String value, String lower) { return value != null && value.toLowerCase().contains(lower); }
    private String trim(String value) { if (value == null) return null; String t = value.trim(); return t.isEmpty() ? null : t; }
    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) { action.run(); return; }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() { @Override public void afterCommit() { action.run(); } });
    }
}
