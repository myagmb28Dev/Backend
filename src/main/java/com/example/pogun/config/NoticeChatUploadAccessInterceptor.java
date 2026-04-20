package com.example.pogun.config;

import com.example.pogun.entity.noticechat.NoticeChatMessageImage;
import com.example.pogun.entity.noticechat.NoticeChatRoom;
import com.example.pogun.entity.noticechat.NoticeChatRoomParticipantState;
import com.example.pogun.entity.user.User;
import com.example.pogun.repository.noticechat.NoticeChatMessageImageRepository;
import com.example.pogun.repository.noticechat.NoticeChatRoomParticipantStateRepository;
import com.example.pogun.repository.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * DM 첨부 파일은 단순 인증이 아니라 해당 채팅방 참여자만 접근할 수 있게 막는다.
 */
@Component
@RequiredArgsConstructor
public class NoticeChatUploadAccessInterceptor implements HandlerInterceptor {
    private static final String NOTICE_CHAT_UPLOAD_PREFIX = "/uploads/notice-chat/";

    private final NoticeChatMessageImageRepository noticeChatMessageImageRepository;
    private final NoticeChatRoomParticipantStateRepository participantStateRepository;
    private final UserRepository userRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = URLDecoder.decode(request.getRequestURI(), StandardCharsets.UTF_8);
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        if (!path.startsWith(NOTICE_CHAT_UPLOAD_PREFIX)) {
            return true;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication != null ? authentication.getPrincipal() : null;
        if (!(principal instanceof String firebaseUid) || firebaseUid.isBlank()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }

        User user = userRepository.findByFirebaseUid(firebaseUid).orElse(null);
        NoticeChatMessageImage image = noticeChatMessageImageRepository.findByAnyUrl(path).orElse(null);
        if (user != null && image != null && isParticipant(image.getMessage().getRoom(), user)) {
            return true;
        }

        NoticeChatRoomParticipantState roomThumbnailState = participantStateRepository.findByCustomThumbnailUrl(path).orElse(null);
        if (user == null || roomThumbnailState == null || !isParticipant(roomThumbnailState.getRoom(), user)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return false;
        }
        return true;
    }

    private boolean isParticipant(NoticeChatRoom room, User user) {
        return room.getOwnerUser().getId().equals(user.getId())
                || room.getGuestUser().getId().equals(user.getId());
    }
}
