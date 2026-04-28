package com.example.pogun.service.admin;

import com.example.pogun.dto.admin.AdminPromoteResponse;
import com.example.pogun.dto.common.ApiResponse.ApiException;
import com.example.pogun.entity.user.User;
import com.example.pogun.entity.user.enums.UserRole;
import com.example.pogun.entity.user.enums.UserStatus;
import com.example.pogun.repository.bookmark.NoticeBookmarkRepository;
import com.example.pogun.repository.community.CommunityCommentRepository;
import com.example.pogun.repository.community.CommunityPostRepository;
import com.example.pogun.repository.missingpet.PetNoticeRepository;
import com.example.pogun.repository.noticechat.NoticeChatMessageImageRepository;
import com.example.pogun.repository.noticechat.NoticeChatMessageRepository;
import com.example.pogun.repository.noticechat.NoticeChatRoomRepository;
import com.example.pogun.repository.report.ReportRepository;
import com.example.pogun.repository.user.UserRepository;
import com.example.pogun.service.adminauth.AdminAuditService;
import com.example.pogun.service.adminauth.AdminPermissionService;
import com.example.pogun.service.adminauth.AdminSecurityService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private CommunityPostRepository communityPostRepository;
    @Mock
    private CommunityCommentRepository communityCommentRepository;
    @Mock
    private PetNoticeRepository petNoticeRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ReportRepository reportRepository;
    @Mock
    private NoticeBookmarkRepository noticeBookmarkRepository;
    @Mock
    private NoticeChatRoomRepository noticeChatRoomRepository;
    @Mock
    private NoticeChatMessageRepository noticeChatMessageRepository;
    @Mock
    private NoticeChatMessageImageRepository noticeChatMessageImageRepository;
    @Mock
    private AdminSecurityService adminSecurityService;
    @Mock
    private AdminAuditService adminAuditService;
    @Mock
    private AdminPermissionService adminPermissionService;

    @InjectMocks
    private AdminService adminService;

    @Test
    void promoteUserToAdmin_updatesRoleToAdmin() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .firebaseUid("user-uid")
                .email("member@example.com")
                .nickname("멤버")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();

        when(userRepository.findByEmail("member@example.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminPromoteResponse response = adminService.promoteUserToAdmin("member@example.com");

        assertThat(response.email()).isEqualTo("member@example.com");
        assertThat(response.role()).isEqualTo("ADMIN");
        assertThat(response.status()).isEqualTo("ACTIVE");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getRole()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void promoteUserToAdmin_rejectsUnknownEmail() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.promoteUserToAdmin("missing@example.com"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("사용자를 찾을 수 없습니다.");
    }
}
