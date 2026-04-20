package com.example.pogun.config;

import com.example.pogun.entity.missingpet.PetNotice;
import com.example.pogun.entity.noticechat.NoticeChatMessage;
import com.example.pogun.entity.noticechat.NoticeChatMessageImage;
import com.example.pogun.entity.noticechat.NoticeChatRoom;
import com.example.pogun.entity.noticechat.enums.NoticeChatRoomStatus;
import com.example.pogun.entity.user.User;
import com.example.pogun.entity.user.enums.UserRole;
import com.example.pogun.entity.user.enums.UserStatus;
import com.example.pogun.repository.noticechat.NoticeChatMessageImageRepository;
import com.example.pogun.repository.noticechat.NoticeChatRoomParticipantStateRepository;
import com.example.pogun.repository.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoticeChatUploadAccessInterceptorTest {

    private final NoticeChatMessageImageRepository imageRepository = mock(NoticeChatMessageImageRepository.class);
    private final NoticeChatRoomParticipantStateRepository participantStateRepository = mock(NoticeChatRoomParticipantStateRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final NoticeChatUploadAccessInterceptor interceptor = new NoticeChatUploadAccessInterceptor(imageRepository, participantStateRepository, userRepository);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void preHandle_allowsNoticeChatUploadForRoomParticipant() throws Exception {
        User owner = user("owner");
        User guest = user("guest");
        NoticeChatMessageImage image = image(owner, guest, "/uploads/notice-chat/messages/owner/file.webp");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("guest", null, List.of()));
        when(userRepository.findByFirebaseUid("guest")).thenReturn(Optional.of(guest));
        when(imageRepository.findByAnyUrl("/uploads/notice-chat/messages/owner/file.webp")).thenReturn(Optional.of(image));

        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean allowed = interceptor.preHandle(request("/uploads/notice-chat/messages/owner/file.webp"), response, new Object());

        assertThat(allowed).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void preHandle_blocksNoticeChatUploadForNonParticipant() throws Exception {
        User owner = user("owner");
        User guest = user("guest");
        User stranger = user("stranger");
        NoticeChatMessageImage image = image(owner, guest, "/uploads/notice-chat/messages/owner/file.webp");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("stranger", null, List.of()));
        when(userRepository.findByFirebaseUid("stranger")).thenReturn(Optional.of(stranger));
        when(imageRepository.findByAnyUrl("/uploads/notice-chat/messages/owner/file.webp")).thenReturn(Optional.of(image));

        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean allowed = interceptor.preHandle(request("/uploads/notice-chat/messages/owner/file.webp"), response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
    }

    private MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        return request;
    }

    private NoticeChatMessageImage image(User owner, User guest, String url) {
        NoticeChatRoom room = NoticeChatRoom.builder()
                .id(UUID.randomUUID())
                .notice(PetNotice.builder().id(UUID.randomUUID()).author(owner).title("공고").build())
                .ownerUser(owner)
                .guestUser(guest)
                .status(NoticeChatRoomStatus.OPEN)
                .build();
        NoticeChatMessage message = NoticeChatMessage.builder()
                .id(UUID.randomUUID())
                .room(room)
                .senderUser(owner)
                .build();
        return NoticeChatMessageImage.builder()
                .id(UUID.randomUUID())
                .message(message)
                .imageUrl(url)
                .build();
    }

    private User user(String uid) {
        return User.builder()
                .id(UUID.randomUUID())
                .firebaseUid(uid)
                .email(uid + "@test.dev")
                .nickname(uid)
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
    }
}
