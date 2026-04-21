package com.example.pogun.config;

import com.example.pogun.dto.common.ApiResponse.ApiException;
import com.example.pogun.entity.noticechat.NoticeChatRoom;
import com.example.pogun.entity.user.User;
import com.example.pogun.entity.user.enums.UserStatus;
import com.example.pogun.repository.noticechat.NoticeChatRoomRepository;
import com.example.pogun.repository.user.UserRepository;
import com.example.pogun.service.user.UserPresenceService;
import com.example.pogun.service.auth.FirebaseIdentityService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.UUID;
/**
 * 애플리케이션 설정을 담당하는 StompAuthChannelInterceptor이다.
 */

@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {
    public static final String SESSION_FIREBASE_UID = "firebaseUid";
    private static final String ROOM_TOPIC_PREFIX = "/topic/chat/rooms/";
    private static final String ROOM_QUEUE_PREFIX = "/user/queue/chat/rooms/";
    private static final String USER_ROOM_TOPIC_PREFIX = "/topic/chat/users/";

    private final FirebaseIdentityService firebaseIdentityService;
    private final UserRepository userRepository;
    private final NoticeChatRoomRepository noticeChatRoomRepository;
    private final UserPresenceService userPresenceService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            // STOMP CONNECT 단계에서 바로 인증을 끝내야 이후 SEND/SUBSCRIBE에서 같은 Principal을 재사용할 수 있다.
            String authorization = accessor.getFirstNativeHeader("Authorization");
            if (authorization == null || !authorization.startsWith("Bearer ")) {
                throw new IllegalArgumentException("웹소켓 인증 토큰이 필요합니다.");
            }

            String token = authorization.substring(7);
            String firebaseUid = resolveUid(token);
            accessor.setUser(new WebSocketPrincipal(firebaseUid));
            userPresenceService.touch(firebaseUid);
            if (accessor.getSessionAttributes() != null) {
                accessor.getSessionAttributes().put(SESSION_FIREBASE_UID, firebaseUid);
            }
        } else {
            restoreUserFromSession(accessor);
        }

        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand()) || SimpMessageType.SUBSCRIBE.equals(accessor.getMessageType())) {
            validateSubscription(accessor);
        }
        return message;
    }

    private String resolveUid(String token) {
        try {
            // HTTP 필터와 동일하게 revoke 여부를 검사해 로그아웃된 토큰의 웹소켓 재접속을 차단한다.
            return firebaseIdentityService.verifyIdToken(token, true).uid();
        } catch (Exception e) {
            throw new IllegalArgumentException("유효하지 않은 웹소켓 인증 토큰입니다.");
        }
    }

    private void validateSubscription(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) {
            return;
        }
        boolean roomTopic = destination.startsWith(ROOM_TOPIC_PREFIX);
        boolean roomQueue = destination.startsWith(ROOM_QUEUE_PREFIX);
        boolean userRoomTopic = destination.startsWith(USER_ROOM_TOPIC_PREFIX);
        if (!roomTopic && !roomQueue && !userRoomTopic) {
            return;
        }

        Principal principal = accessor.getUser();
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw ApiException.unauthorized("WEBSOCKET_UNAUTHORIZED", "웹소켓 사용자 인증 정보를 찾을 수 없습니다.");
        }

        User user = userRepository.findByFirebaseUid(principal.getName())
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));
        if (user.getStatus() != null && user.getStatus() != UserStatus.ACTIVE) {
            throw ApiException.forbidden("CHAT_USER_FORBIDDEN", "채팅을 구독할 수 없는 사용자 상태입니다.");
        }

        DestinationInfo destinationInfo = extractDestinationInfo(destination);
        if (destinationInfo.userId() != null && !destinationInfo.userId().equals(user.getId())) {
            throw ApiException.forbidden("CHAT_ROOM_FORBIDDEN", "본인에게 전달되는 채널만 구독할 수 있습니다.");
        }
        if (destinationInfo.roomId() == null) {
            return;
        }

        NoticeChatRoom room = noticeChatRoomRepository.findById(destinationInfo.roomId())
                .orElseThrow(() -> ApiException.notFound("CHAT_ROOM_NOT_FOUND", "채팅방을 찾을 수 없습니다."));

        boolean participant = room.getOwnerUser().getId().equals(user.getId())
                || room.getGuestUser().getId().equals(user.getId());
        if (!participant) {
            throw ApiException.forbidden("CHAT_ROOM_FORBIDDEN", "해당 채팅방을 구독할 수 없습니다.");
        }
    }

    private void restoreUserFromSession(StompHeaderAccessor accessor) {
        Principal principal = accessor.getUser();
        if (principal != null
                && principal.getName() != null
                && !principal.getName().isBlank()
                && !"anonymousUser".equals(principal.getName())) {
            return;
        }
        if (accessor.getSessionAttributes() == null) {
            return;
        }

        Object firebaseUid = accessor.getSessionAttributes().get(SESSION_FIREBASE_UID);
        if (firebaseUid instanceof String uid && !uid.isBlank()) {
            accessor.setUser(new WebSocketPrincipal(uid));
        }
    }

    private DestinationInfo extractDestinationInfo(String destination) {
        String roomSegment;
        UUID userId = null;
        if (destination.startsWith(ROOM_TOPIC_PREFIX)) {
            roomSegment = destination.substring(ROOM_TOPIC_PREFIX.length());
        } else if (destination.startsWith(ROOM_QUEUE_PREFIX)) {
            roomSegment = destination.substring(ROOM_QUEUE_PREFIX.length());
        } else if (destination.startsWith(USER_ROOM_TOPIC_PREFIX)) {
            String tail = destination.substring(USER_ROOM_TOPIC_PREFIX.length());
            String[] segments = tail.split("/");
            if (segments.length < 2 || !"rooms".equals(segments[1])) {
                throw ApiException.badRequest("INVALID_CHAT_DESTINATION", "지원하지 않는 채팅 구독 경로입니다.");
            }
            try {
                userId = UUID.fromString(segments[0]);
            } catch (IllegalArgumentException e) {
                throw ApiException.badRequest("INVALID_CHAT_USER_ID", "올바르지 않은 사용자 ID 형식입니다.");
            }
            if (segments.length == 2) {
                return new DestinationInfo(userId, null);
            }
            roomSegment = segments[2];
        } else {
            throw ApiException.badRequest("INVALID_CHAT_DESTINATION", "지원하지 않는 채팅 구독 경로입니다.");
        }
        int slashIndex = roomSegment.indexOf('/');
        String roomId = slashIndex >= 0 ? roomSegment.substring(0, slashIndex) : roomSegment;
        try {
            return new DestinationInfo(userId, UUID.fromString(roomId));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("INVALID_CHAT_ROOM_ID", "올바르지 않은 채팅방 ID 형식입니다.");
        }
    }

    private record DestinationInfo(UUID userId, UUID roomId) {
    }
}

