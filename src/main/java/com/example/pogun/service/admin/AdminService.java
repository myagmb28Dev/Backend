package com.example.pogun.service.admin;

import com.example.pogun.dto.admin.AdminDashboardResponse;
import com.example.pogun.dto.admin.AdminDeleteResponse;
import com.example.pogun.dto.admin.AdminUserSanctionResponse;
import com.example.pogun.dto.admin.AdminVisibilityResponse;
import com.example.pogun.entity.community.CommunityPost;
import com.example.pogun.entity.noticechat.NoticeChatRoom;
import com.example.pogun.entity.missingpet.PetNotice;
import com.example.pogun.entity.user.User;
import com.example.pogun.entity.community.CommunityPostStatus;
import com.example.pogun.entity.report.ReportStatus;
import com.example.pogun.entity.user.UserStatus;
import com.example.pogun.dto.common.ApiResponse.ApiException;
import com.example.pogun.repository.community.CommunityPostRepository;
import com.example.pogun.repository.bookmark.NoticeBookmarkRepository;
import com.example.pogun.repository.noticechat.NoticeChatMessageRepository;
import com.example.pogun.repository.noticechat.NoticeChatRoomRepository;
import com.example.pogun.repository.missingpet.PetNoticeRepository;
import com.example.pogun.repository.report.ReportRepository;
import com.example.pogun.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
/**
 * 도메인 비즈니스 로직을 담당하는 AdminService이다.
 */

@Service
@RequiredArgsConstructor
public class AdminService {
    private static final Set<ReportStatus> PENDING_REPORT_STATUSES = Set.of(ReportStatus.RECEIVED, ReportStatus.REVIEWING);

    private final CommunityPostRepository communityPostRepository;
    private final PetNoticeRepository petNoticeRepository;
    private final UserRepository userRepository;
    private final ReportRepository reportRepository;
    private final NoticeBookmarkRepository noticeBookmarkRepository;
    private final NoticeChatRoomRepository noticeChatRoomRepository;
    private final NoticeChatMessageRepository noticeChatMessageRepository;

    // 대시보드는 여러 도메인 저장소에서 바로 집계해 관리자 첫 화면이 별도 후처리 없이 그릴 수 있게 반환한다.
    public AdminDashboardResponse getDashboard() {
        ZoneId zoneId = ZoneId.systemDefault();
        Instant startOfToday = LocalDate.now(zoneId).atStartOfDay(zoneId).toInstant();
        Instant startOfTomorrow = LocalDate.now(zoneId).plusDays(1).atStartOfDay(zoneId).toInstant();
        return new AdminDashboardResponse(
                reportRepository.countByCreatedAtBetween(startOfToday, startOfTomorrow),
                reportRepository.countByStatusIn(PENDING_REPORT_STATUSES),
                communityPostRepository.countByStatus(CommunityPostStatus.HIDDEN),
                petNoticeRepository.countByHiddenTrue(),
                userRepository.countByStatus(UserStatus.BANNED)
        );
    }

    @Transactional
    public AdminVisibilityResponse updateCommunityVisibility(String postId, String visibility) {
        CommunityPost post = getCommunityPost(postId);
        CommunityPostStatus nextStatus = isVisible(visibility) ? CommunityPostStatus.ACTIVE : CommunityPostStatus.HIDDEN;
        post.setStatus(nextStatus);
        CommunityPost saved = communityPostRepository.save(post);
        return new AdminVisibilityResponse(saved.getId(), toVisibility(saved.getStatus() == CommunityPostStatus.ACTIVE), saved.getStatus().name(), null);
    }

    @Transactional
    public AdminVisibilityResponse updateMissingPostVisibility(String postId, String visibility) {
        PetNotice notice = getPetNotice(postId);
        boolean visible = isVisible(visibility);
        notice.setHidden(!visible);
        PetNotice saved = petNoticeRepository.save(notice);
        return new AdminVisibilityResponse(saved.getId(), toVisibility(!Boolean.TRUE.equals(saved.getHidden())), null, saved.getHidden());
    }

    @Transactional
    public AdminUserSanctionResponse sanctionUser(String userId, String action) {
        User user = getUser(userId);
        UserStatus nextStatus = parseUserStatus(action);
        user.setStatus(nextStatus);
        User saved = userRepository.save(user);
        return new AdminUserSanctionResponse(saved.getId(), action.trim().toUpperCase(), saved.getStatus().name());
    }

    // 공고 삭제 전에 북마크와 채팅 흔적을 먼저 비워 연관 데이터가 고아 상태로 남지 않게 정리한다.
    @Transactional
    public AdminDeleteResponse deleteMissingPost(String postId) {
        PetNotice notice = getPetNotice(postId);
        List<NoticeChatRoom> chatRooms = noticeChatRoomRepository.findByNotice(notice);
        if (!chatRooms.isEmpty()) {
            noticeChatMessageRepository.deleteByRoomIn(chatRooms);
            noticeChatRoomRepository.deleteAll(chatRooms);
        }
        noticeBookmarkRepository.deleteByNotice(notice);
        petNoticeRepository.delete(notice);
        return new AdminDeleteResponse(notice.getId(), true, null);
    }

    @Transactional
    public AdminDeleteResponse deleteCommunityPost(String postId) {
        CommunityPost post = getCommunityPost(postId);
        post.setStatus(CommunityPostStatus.DELETED);
        CommunityPost saved = communityPostRepository.save(post);
        return new AdminDeleteResponse(saved.getId(), true, saved.getStatus().name());
    }

    private CommunityPost getCommunityPost(String postId) {
        return communityPostRepository.findById(parseUuid(postId, "INVALID_COMMUNITY_POST_ID", "올바르지 않은 커뮤니티 글 ID 형식입니다.")).orElseThrow(() -> ApiException.notFound("COMMUNITY_POST_NOT_FOUND", "커뮤니티 게시글을 찾을 수 없습니다."));
    }

    private PetNotice getPetNotice(String postId) {
        return petNoticeRepository.findById(parseUuid(postId, "INVALID_NOTICE_ID", "올바르지 않은 공고 ID 형식입니다.")).orElseThrow(() -> ApiException.notFound("NOTICE_NOT_FOUND", "실종 공고를 찾을 수 없습니다."));
    }

    private User getUser(String userId) {
        return userRepository.findById(parseUuid(userId, "INVALID_USER_ID", "올바르지 않은 사용자 ID 형식입니다.")).orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));
    }

    private UUID parseUuid(String value, String code, String message) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest(code, message);
        }
    }

    private boolean isVisible(String visibility) {
        if (visibility == null || visibility.isBlank()) throw ApiException.badRequest("INVALID_VISIBILITY", "visibility는 필수입니다.");
        return switch (visibility.trim().toUpperCase()) {
            case "VISIBLE", "PUBLIC", "SHOW" -> true;
            case "HIDDEN", "HIDE", "PRIVATE" -> false;
            default -> throw ApiException.badRequest("INVALID_VISIBILITY", "올바르지 않은 visibility 값입니다.");
        };
    }

    private String toVisibility(boolean visible) { return visible ? "VISIBLE" : "HIDDEN"; }

    private UserStatus parseUserStatus(String action) {
        if (action == null || action.isBlank()) throw ApiException.badRequest("INVALID_SANCTION_ACTION", "action은 필수입니다.");
        return switch (action.trim().toUpperCase()) {
            case "BAN", "BANNED", "TEMP_SUSPEND", "SUSPEND" -> UserStatus.BANNED;
            case "UNBAN", "RELEASE", "ACTIVATE", "ACTIVE" -> UserStatus.ACTIVE;
            case "WITHDRAW", "WITHDRAWN" -> UserStatus.WITHDRAWN;
            default -> throw ApiException.badRequest("INVALID_SANCTION_ACTION", "올바르지 않은 action 값입니다.");
        };
    }
}