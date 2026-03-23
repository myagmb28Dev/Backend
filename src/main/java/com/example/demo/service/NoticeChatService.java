package com.example.demo.service;
import com.example.demo.dto.NoticeChatMessageRequest;
import com.example.demo.dto.NoticeChatTypingRequest;
import com.example.demo.entity.NoticeChatMessage;
import com.example.demo.entity.NoticeChatRoom;
import com.example.demo.entity.PetNotice;
import com.example.demo.entity.User;
import com.example.demo.entity.enums.NoticeChatRoomStatus;
import com.example.demo.repository.NoticeChatMessageRepository;
import com.example.demo.repository.NoticeChatRoomRepository;
import com.example.demo.repository.PetNoticeRepository;
import com.example.demo.repository.UserRepository;
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

@Service
@Slf4j
@RequiredArgsConstructor
public class NoticeChatService {
    private final NoticeChatRoomRepository noticeChatRoomRepository;
    private final NoticeChatMessageRepository noticeChatMessageRepository;
    private final PetNoticeRepository petNoticeRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate simpMessagingTemplate;

    @Transactional
    public Map<String, Object> createOrGetRoom(String noticeId) {
        User currentUser = getCurrentUser();
        PetNotice notice = getNotice(noticeId);
        User owner = notice.getAuthor();

        if (owner.getId().equals(currentUser.getId())) {
            throw new RuntimeException("본인 공고에는 메시지 방을 생성할 수 없습니다.");
        }

        NoticeChatRoom room = noticeChatRoomRepository.findByNoticeAndOwnerUserAndGuestUser(notice, owner, currentUser)
                .orElseGet(() -> noticeChatRoomRepository.save(NoticeChatRoom.builder()
                        .notice(notice)
                        .ownerUser(owner)
                        .guestUser(currentUser)
                        .status(NoticeChatRoomStatus.OPEN)
                        .build()));

        return toRoomResponse(room, currentUser);
    }

    public List<Map<String, Object>> getRooms() {
        User currentUser = getCurrentUser();
        return noticeChatRoomRepository.findByOwnerUserIdOrGuestUserIdOrderByLastMessageAtDescCreatedAtDesc(currentUser.getId(), currentUser.getId()).stream()
                .map(room -> toRoomResponse(room, currentUser))
                .toList();
    }

    @Transactional
    public List<Map<String, Object>> getMessages(String roomId) {
        User currentUser = getCurrentUser();
        NoticeChatRoom room = getAccessibleRoom(roomId, currentUser);
        log.info("채팅 메시지 조회 시작 roomId={} userId={} firebaseUid={}",
                room.getId(), currentUser.getId(), currentUser.getFirebaseUid());

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

    @Transactional
    public Map<String, Object> sendMessage(Principal principal, NoticeChatMessageRequest request) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new RuntimeException("웹소켓 사용자 인증 정보를 찾을 수 없습니다.");
        }
        User sender = getUserByFirebaseUid(principal.getName());
        NoticeChatRoom room = getAccessibleRoom(request.getRoomId().toString(), sender);
        log.info("채팅 메시지 전송 시작 roomId={} senderUserId={} firebaseUid={} rawMessage={}",
                room.getId(), sender.getId(), sender.getFirebaseUid(), request.getMessage());

        if (room.getStatus() == NoticeChatRoomStatus.CLOSED) {
            throw new RuntimeException("종료된 채팅방입니다.");
        }

        String content = trimToNull(request.getMessage());
        if (content == null) {
            throw new RuntimeException("메시지 내용은 비어 있을 수 없습니다.");
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

        Map<String, Object> payload = toMessageResponse(saved, sender);
        simpMessagingTemplate.convertAndSend("/topic/chat/rooms/" + room.getId(), (Object) payload);
        log.info("채팅 메시지 브로드캐스트 완료 roomId={} messageId={}", room.getId(), saved.getId());
        return payload;
    }

    public Map<String, Object> sendTypingEvent(Principal principal, NoticeChatTypingRequest request) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new RuntimeException("웹소켓 사용자 인증 정보를 찾을 수 없습니다.");
        }
        if (request.getRoomId() == null) {
            throw new RuntimeException("채팅방 ID는 필수입니다.");
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
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));
    }

    private PetNotice getNotice(String noticeId) {
        try {
            UUID parsedId = UUID.fromString(noticeId);
            return petNoticeRepository.findById(parsedId)
                    .orElseThrow(() -> new RuntimeException("실종 공고를 찾을 수 없습니다."));
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("올바르지 않은 공고 ID 형식입니다.");
        }
    }

    private NoticeChatRoom getAccessibleRoom(String roomId, User currentUser) {
        NoticeChatRoom room;
        try {
            room = noticeChatRoomRepository.findById(UUID.fromString(roomId))
                    .orElseThrow(() -> new RuntimeException("채팅방을 찾을 수 없습니다."));
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("올바르지 않은 채팅방 ID 형식입니다.");
        }

        boolean participant = room.getOwnerUser().getId().equals(currentUser.getId())
                || room.getGuestUser().getId().equals(currentUser.getId());
        if (!participant) {
            throw new RuntimeException("해당 채팅방에 접근할 수 없습니다.");
        }
        return room;
    }

    private Map<String, Object> toRoomResponse(NoticeChatRoom room, User currentUser) {
        User opponent = room.getOwnerUser().getId().equals(currentUser.getId()) ? room.getGuestUser() : room.getOwnerUser();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("roomId", room.getId());
        response.put("noticeId", room.getNotice().getId());
        response.put("noticeTitle", room.getNotice().getTitle());
        response.put("roomStatus", room.getStatus().name());
        response.put("lastMessageAt", room.getLastMessageAt());
        response.put("createdAt", room.getCreatedAt());
        response.put("opponentUserId", opponent.getId());
        response.put("opponentNickname", opponent.getNickname());
        response.put("unreadCount", noticeChatMessageRepository.countByRoomAndSenderUserNotAndIsReadFalse(room, currentUser));
        return response;
    }

    private Map<String, Object> toMessageResponse(NoticeChatMessage message, User currentUser) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", message.getId());
        response.put("roomId", message.getRoom().getId());
        response.put("senderUserId", message.getSenderUser().getId());
        response.put("senderNickname", message.getSenderUser().getNickname());
        response.put("message", message.getMessage());
        response.put("isRead", message.getIsRead());
        response.put("mine", message.getSenderUser().getId().equals(currentUser.getId()));
        response.put("createdAt", message.getCreatedAt());
        return response;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
