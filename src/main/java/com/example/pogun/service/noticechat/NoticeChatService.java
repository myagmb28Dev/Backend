package com.example.pogun.service.noticechat;

import com.example.pogun.dto.common.ApiResponse.ApiException;
import com.example.pogun.dto.noticechat.NoticeChatMessageRequest;
import com.example.pogun.dto.noticechat.NoticeChatMessageResponse;
import com.example.pogun.dto.noticechat.NoticeChatRoomCreateResult;
import com.example.pogun.dto.noticechat.NoticeChatRoomResponse;
import com.example.pogun.dto.noticechat.NoticeChatTypingRequest;
import com.example.pogun.entity.noticechat.NoticeChatMessage;
import com.example.pogun.entity.noticechat.NoticeChatRoom;
import com.example.pogun.entity.missingpet.PetNotice;
import com.example.pogun.entity.user.User;
import com.example.pogun.entity.noticechat.enums.NoticeChatRoomStatus;
import com.example.pogun.repository.noticechat.NoticeChatMessageRepository;
import com.example.pogun.repository.noticechat.NoticeChatRoomRepository;
import com.example.pogun.repository.missingpet.PetNoticeRepository;
import com.example.pogun.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
/**
 * 도메인 비즈니스 로직을 담당하는 NoticeChatService이다.
 */

@Service
@Slf4j
@RequiredArgsConstructor
public class NoticeChatService {
    private final NoticeChatRoomRepository noticeChatRoomRepository;
    private final NoticeChatMessageRepository noticeChatMessageRepository;
    private final PetNoticeRepository petNoticeRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate simpMessagingTemplate;

    // 같은 공고에 대해 같은 발견자-작성자 조합은 항상 하나의 방만 쓰도록 보장한다.
    @Transactional
    public NoticeChatRoomCreateResult createOrGetRoom(String noticeId) {
        User currentUser = getCurrentUser();
        PetNotice notice = getNotice(noticeId);
        User owner = notice.getAuthor();

        if (owner.getId().equals(currentUser.getId())) {
            throw ApiException.conflict("CHAT_ROOM_SELF_NOTICE", "본인 공고에는 메시지 방을 생성할 수 없습니다.");
        }

        return noticeChatRoomRepository.findByNoticeAndOwnerUserAndGuestUser(notice, owner, currentUser)
                .map(room -> new NoticeChatRoomCreateResult(false, toRoomResponse(room, currentUser)))
                .orElseGet(() -> {
                    NoticeChatRoom saved = noticeChatRoomRepository.save(NoticeChatRoom.builder()
                            .notice(notice)
                            .ownerUser(owner)
                            .guestUser(currentUser)
                            .status(NoticeChatRoomStatus.OPEN)
                            .build());
                    return new NoticeChatRoomCreateResult(true, toRoomResponse(saved, currentUser));
                });
    }

    public List<NoticeChatRoomResponse> getRooms() {
        User currentUser = getCurrentUser();
        return noticeChatRoomRepository.findByOwnerUserIdOrGuestUserIdOrderByLastMessageAtDescCreatedAtDesc(currentUser.getId(), currentUser.getId()).stream()
                .map(room -> toRoomResponse(room, currentUser))
                .toList();
    }

    // 방을 여는 순간 상대 메시지를 읽음 처리해서, 별도 read API 없이도 기본 DM UX가 성립되게 한다.
    @Transactional
    public List<NoticeChatMessageResponse> getMessages(String roomId) {
        User currentUser = getCurrentUser();
        NoticeChatRoom room = getAccessibleRoom(roomId, currentUser);
        log.info("채팅 메시지 조회 시작 roomId={} userId={}",
                room.getId(), currentUser.getId());

        List<NoticeChatMessage> messages = noticeChatMessageRepository.findByRoomOrderByCreatedAtAsc(room);
        boolean changed = false;
        for (NoticeChatMessage message : messages) {
            if (!message.getSenderUser().getId().equals(currentUser.getId()) && !Boolean.TRUE.equals(message.getIsRead())) {
                message.setIsRead(true);
                changed = true;
            }
        }
        if (changed) {
            noticeChatMessageRepository.saveAll(messages);
            log.info("채팅 메시지 읽음 처리 roomId={} updatedCount={} readerUserId={}",
                    room.getId(),
                    messages.stream()
                            .filter(message -> !message.getSenderUser().getId().equals(currentUser.getId()))
                            .count(),
                    currentUser.getId());
        }

        log.info("채팅 메시지 조회 완료 roomId={} messageCount={} userId={}",
                room.getId(), messages.size(), currentUser.getId());

        return messages.stream().map(message -> toMessageResponse(message, currentUser)).toList();
    }

    // 메시지는 브로드캐스트 전에 DB에 먼저 저장해 재접속 시 히스토리와 실시간 화면이 같은 원본을 보게 한다.
    @Transactional
    public NoticeChatMessageResponse sendMessage(Principal principal, NoticeChatMessageRequest request) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw ApiException.unauthorized("WEBSOCKET_UNAUTHORIZED", "웹소켓 사용자 인증 정보를 찾을 수 없습니다.");
        }
        User sender = getUserByFirebaseUid(principal.getName());
        NoticeChatRoom room = getAccessibleRoom(request.getRoomId().toString(), sender);
        String content = trimToNull(request.getMessage());
        log.info("채팅 메시지 전송 시작 roomId={} senderUserId={} messageLength={}",
                room.getId(), sender.getId(), content == null ? 0 : content.length());

        if (room.getStatus() == NoticeChatRoomStatus.CLOSED) {
            throw ApiException.conflict("CHAT_ROOM_CLOSED", "종료된 채팅방입니다.");
        }

        if (content == null) {
            throw ApiException.badRequest("EMPTY_MESSAGE", "메시지 내용은 비어 있을 수 없습니다.");
        }

        NoticeChatMessage saved = noticeChatMessageRepository.save(NoticeChatMessage.builder()
                .room(room)
                .senderUser(sender)
                .message(content)
                .build());

        room.setLastMessageAt(saved.getCreatedAt() != null ? saved.getCreatedAt() : Instant.now());
        noticeChatRoomRepository.save(room);
        log.info("채팅 메시지 저장 완료 roomId={} messageId={} senderUserId={} createdAt={}",
                room.getId(), saved.getId(), sender.getId(), saved.getCreatedAt());

        NoticeChatMessageResponse payload = toMessageResponse(saved, sender);
        simpMessagingTemplate.convertAndSend("/topic/chat/rooms/" + room.getId(), payload);
        log.info("채팅 메시지 브로드캐스트 완료 roomId={} messageId={}", room.getId(), saved.getId());
        return payload;
    }

    // typing 이벤트는 영속화하지 않고 상대 화면에만 중계하는 휘발성 상태다.
    public Map<String, Object> sendTypingEvent(Principal principal, NoticeChatTypingRequest request) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw ApiException.unauthorized("WEBSOCKET_UNAUTHORIZED", "웹소켓 사용자 인증 정보를 찾을 수 없습니다.");
        }
        if (request.getRoomId() == null) {
            throw ApiException.badRequest("MISSING_ROOM_ID", "채팅방 ID는 필수입니다.");
        }

        User sender = getUserByFirebaseUid(principal.getName());
        NoticeChatRoom room = getAccessibleRoom(request.getRoomId().toString(), sender);
        log.info("채팅 입력중 이벤트 roomId={} senderUserId={} isTyping={}",
                room.getId(), sender.getId(), Boolean.TRUE.equals(request.getTyping()));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("roomId", room.getId());
        payload.put("senderUserId", sender.getId());
        payload.put("senderNickname", sender.getNickname());
        payload.put("isTyping", Boolean.TRUE.equals(request.getTyping()));

        simpMessagingTemplate.convertAndSend("/topic/chat/rooms/" + room.getId() + "/typing", (Object) payload);
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

    private PetNotice getNotice(String noticeId) {
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
        return room;
    }

    private NoticeChatRoomResponse toRoomResponse(NoticeChatRoom room, User currentUser) {
        User opponent = room.getOwnerUser().getId().equals(currentUser.getId()) ? room.getGuestUser() : room.getOwnerUser();
        return new NoticeChatRoomResponse(
                room.getId(),
                room.getNotice().getId(),
                room.getNotice().getTitle(),
                room.getStatus().name(),
                room.getLastMessageAt(),
                room.getCreatedAt(),
                opponent.getId(),
                opponent.getNickname(),
                noticeChatMessageRepository.countByRoomAndSenderUserNotAndIsReadFalse(room, currentUser)
        );
    }

    private NoticeChatMessageResponse toMessageResponse(NoticeChatMessage message, User currentUser) {
        return new NoticeChatMessageResponse(
                message.getId(),
                message.getRoom().getId(),
                message.getSenderUser().getId(),
                message.getSenderUser().getNickname(),
                message.getMessage(),
                message.getIsRead(),
                message.getSenderUser().getId().equals(currentUser.getId()),
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
}

