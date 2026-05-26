package com.example.pogun.service.admin;

import com.example.pogun.config.firebase.FirebaseAuthProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.example.pogun.dto.admin.AdminNotificationSendRequest;
import com.example.pogun.dto.common.ApiResponse.ApiException;
import com.example.pogun.entity.admin.AdminIntegrationStatus;
import com.example.pogun.entity.admin.AdminNotificationDispatch;
import com.example.pogun.entity.admin.AdminReferenceData;
import com.example.pogun.entity.admin.AdminSetting;
import com.example.pogun.entity.admin.enums.AdminPermission;
import com.example.pogun.entity.community.CommunityComment;
import com.example.pogun.entity.community.CommunityPost;
import com.example.pogun.entity.community.CommunityPostReaction;
import com.example.pogun.entity.community.CommunityPostVote;
import com.example.pogun.entity.community.enums.CommunityCommentStatus;
import com.example.pogun.entity.community.enums.CommunityPostStatus;
import com.example.pogun.entity.missingpet.PetNotice;
import com.example.pogun.entity.missingpet.PetNoticeImage;
import com.example.pogun.entity.missingpet.enums.PetNoticeStatus;
import com.example.pogun.entity.notification.enums.NotificationPriority;
import com.example.pogun.entity.notification.enums.NotificationTargetType;
import com.example.pogun.entity.notification.enums.NotificationType;
import com.example.pogun.entity.report.Report;
import com.example.pogun.entity.report.enums.ReportStatus;
import com.example.pogun.entity.report.enums.ReportTargetType;
import com.example.pogun.entity.user.User;
import com.example.pogun.entity.user.enums.UserRole;
import com.example.pogun.entity.user.enums.UserStatus;
import com.example.pogun.repository.admin.AdminAuditLogRepository;
import com.example.pogun.repository.admin.AdminIntegrationStatusRepository;
import com.example.pogun.repository.admin.AdminNotificationDispatchRepository;
import com.example.pogun.repository.admin.AdminReferenceDataRepository;
import com.example.pogun.repository.admin.AdminSettingRepository;
import com.example.pogun.repository.community.CommunityCommentRepository;
import com.example.pogun.repository.community.CommunityPostRepository;
import com.example.pogun.repository.community.CommunityPostReactionRepository;
import com.example.pogun.repository.community.CommunityPostVoteRepository;
import com.example.pogun.repository.missingpet.PetNoticeRepository;
import com.example.pogun.repository.report.ReportRepository;
import com.example.pogun.repository.user.UserRepository;
import com.example.pogun.service.adminauth.AdminAuditService;
import com.example.pogun.service.adminauth.AdminPermissionService;
import com.example.pogun.service.adminauth.AdminSecurityService;
import com.example.pogun.service.notification.NotificationService;
import com.example.pogun.service.presence.PresenceSessionStore;
import com.example.pogun.service.user.UserPresenceService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AdminConsoleService {

    private static final TypeReference<Map<String, Object>> STRING_OBJECT_MAP_TYPE = new TypeReference<>() {};
    private static final Set<ReportStatus> ACTIVE_REPORT_STATUSES = Set.of(ReportStatus.RECEIVED, ReportStatus.REVIEWING);
    private static final String USER_APP_DOMAIN = "paw.gbsw.hs.kr";

    private final UserRepository userRepository;
    private final PetNoticeRepository petNoticeRepository;
    private final CommunityPostRepository communityPostRepository;
    private final CommunityPostReactionRepository communityPostReactionRepository;
    private final CommunityPostVoteRepository communityPostVoteRepository;
    private final CommunityCommentRepository communityCommentRepository;
    private final ReportRepository reportRepository;
    private final AdminNotificationDispatchRepository adminNotificationDispatchRepository;
    private final AdminIntegrationStatusRepository adminIntegrationStatusRepository;
    private final AdminReferenceDataRepository adminReferenceDataRepository;
    private final AdminSettingRepository adminSettingRepository;
    private final AdminAuditLogRepository adminAuditLogRepository;
    private final NotificationService notificationService;
    private final AdminService adminService;
    private final AdminSecurityService adminSecurityService;
    private final AdminPermissionService adminPermissionService;
    private final AdminAuditService adminAuditService;
    private final PresenceSessionStore presenceSessionStore;
    private final UserPresenceService userPresenceService;
    private final DataSource dataSource;
    private final RedisConnectionFactory redisConnectionFactory;
    private final FirebaseAuth firebaseAuth;
    private final FirebaseAuthProperties firebaseAuthProperties;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Map<String, Object> dashboardSummary(String fromValue, String toValue) {
        adminSecurityService.require(AdminPermission.AUDIT_READ);
        Instant from = resolveFrom(fromValue);
        Instant to = resolveTo(toValue);
        List<AdminNotificationDispatch> dispatches = adminNotificationDispatchRepository.findAll(PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt"))).getContent();

        List<User> activeUsersList = resolveActiveUsersSafely();
        long activeUsers = activeUsersList.size();
        long connectedUsers = resolveConnectedUsersSafely();
        String serverStatus = latestGlobalStatus();
        long newUsers = userRepository.countByCreatedAtBetween(from, to);
        long announcementsPosted = petNoticeRepository.countByCreatedAtBetween(from, to);
        long communityPosts = communityPostRepository.countByCreatedAtBetween(from, to);
        long processedTasks = reportRepository.countByReviewedAtBetweenAndStatusIn(from, to, List.of(ReportStatus.RESOLVED, ReportStatus.REJECTED));
        long userReports = reportRepository.countByCreatedAtBetween(from, to);
        List<Map<String, Object>> recentReports = reportRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 5)).getContent().stream()
                .map(this::toReportSummaryMap)
                .toList();

        return orderedMap(
                "activeUsers", activeUsers,
                "connectedUsers", connectedUsers,
                "serverStatus", serverStatus,
                "newUsers", newUsers,
                "announcementsPosted", announcementsPosted,
                "communityPosts", communityPosts,
                "processedTasks", processedTasks,
                "userReports", userReports,
                "kpiToday", kpiForRange(LocalDate.now(ZoneId.systemDefault()), LocalDate.now(ZoneId.systemDefault())),
                "kpiWeekly", kpiForRange(LocalDate.now(ZoneId.systemDefault()).minusDays(6), LocalDate.now(ZoneId.systemDefault())),
                "kpiMonthly", kpiForRange(LocalDate.now(ZoneId.systemDefault()).withDayOfMonth(1), LocalDate.now(ZoneId.systemDefault())),
                "recentReports", recentReports,
                "recentDispatchFailures", dispatches.stream()
                        .filter(dispatch -> dispatch.getFailedCount() > 0)
                        .map(this::toDispatchMap)
                        .toList()
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> dashboardTimeline(String fromValue, String toValue, String granularity) {
        adminSecurityService.require(AdminPermission.AUDIT_READ);
        ZoneId zoneId = ZoneId.systemDefault();
        LocalDate from = resolveDate(fromValue, LocalDate.now(zoneId).minusDays(6));
        LocalDate to = resolveDate(toValue, LocalDate.now(zoneId));
        List<Map<String, Object>> series = new ArrayList<>();

        for (LocalDate cursor = from; !cursor.isAfter(to); cursor = cursor.plusDays(1)) {
            Instant start = cursor.atStartOfDay(zoneId).toInstant();
            Instant end = cursor.plusDays(1).atStartOfDay(zoneId).toInstant();
            series.add(Map.of(
                    "label", cursor.toString(),
                    "notices", petNoticeRepository.countByCreatedAtBetween(start, end),
                    "reports", reportRepository.countByCreatedAtBetween(start, end),
                    "users", userRepository.countByCreatedAtBetween(start, end)
            ));
        }

        return orderedMap(
                "granularity", granularity == null || granularity.isBlank() ? "day" : granularity,
                "series", series
        );
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> dashboardPriorities() {
        adminSecurityService.require(AdminPermission.REPORT_REVIEW);
        List<Report> activeReports = reportRepository.findByStatusInOrderByCreatedAtDesc(ACTIVE_REPORT_STATUSES);
        Map<String, Long> counts = activeReports.stream()
                .filter(report -> ACTIVE_REPORT_STATUSES.contains(report.getStatus()))
                .collect(Collectors.groupingBy(this::priorityKey, Collectors.counting()));
        return activeReports.stream()
                .filter(report -> ACTIVE_REPORT_STATUSES.contains(report.getStatus()))
                .filter(report -> counts.getOrDefault(priorityKey(report), 0L) >= 10L)
                .sorted(Comparator.comparing(Report::getCreatedAt, Comparator.nullsLast(Instant::compareTo)).reversed())
                .map(report -> Map.<String, Object>of(
                        "id", report.getId(),
                        "type", normalizeReportTargetType(report.getTargetType()),
                        "targetLabel", resolvePriorityTargetLabel(report),
                        "reason", "신고 누적 " + counts.get(priorityKey(report)) + "건으로 우선 검토 필요",
                        "priority", true
                ))
                .distinct()
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listUsers(String query, String statusValue, String roleValue, String createdFrom, String createdTo,
                                         Integer page, Integer pageSize, String sortBy, String sortOrder) {
        adminSecurityService.require(AdminPermission.AUDIT_READ);
        int resolvedPage = Math.max((page == null ? 1 : page) - 1, 0);
        int resolvedPageSize = Math.min(Math.max(pageSize == null ? 20 : pageSize, 1), 100);
        Instant from = blank(createdFrom) ? null : parseInstant(createdFrom);
        Instant to = blank(createdTo) ? null : parseInstant(createdTo);
        UserStatus status = parseNullableUserStatus(statusValue);
        UserRole role = parseNullableRole(roleValue);
        String normalizedQuery = blank(query) ? null : query.trim().toLowerCase(Locale.ROOT);
        Specification<User> spec = (root, criteriaQuery, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (role != null) {
                predicates.add(cb.equal(root.get("role"), role));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            if (normalizedQuery != null) {
                String pattern = "%" + normalizedQuery + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("email")), pattern),
                        cb.like(cb.lower(root.get("nickname")), pattern)
                ));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        Pageable pageable = PageRequest.of(
                resolvedPage,
                resolvedPageSize,
                "asc".equalsIgnoreCase(sortOrder) ? Sort.Direction.ASC : Sort.Direction.DESC,
                resolveUserSortProperty(sortBy)
        );

        var resultPage = userRepository.findAll(spec, pageable);
        List<Map<String, Object>> items = resultPage.getContent().stream()
                .map(this::toUserSummaryMap)
                .toList();

        return orderedMap(
                "items", items,
                "page", resolvedPage + 1,
                "pageSize", resolvedPageSize,
                "total", resultPage.getTotalElements()
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> userDetail(String userId) {
        adminSecurityService.require(AdminPermission.AUDIT_READ);
        User user = getUser(userId);
        List<Report> reports = reportRepository.findAll().stream()
                .filter(report -> report.getReporter().getId().equals(user.getId()) || isReportTargetingUser(report, user))
                .sorted(Comparator.comparing(Report::getCreatedAt, Comparator.nullsLast(Instant::compareTo)).reversed())
                .toList();
        return orderedMap(
                "id", user.getId(),
                "name", user.getNickname(),
                "email", user.getEmail(),
                "role", user.getRole().name(),
                "status", normalizeUserStatus(user.getStatus()),
                "createdAt", user.getCreatedAt(),
                "lastLoginAt", user.getLastActiveAt(),
                "profile", orderedMap(
                        "region", safe(user.getRegion()),
                        "phoneNumber", safe(user.getPhoneNumber()),
                        "profileImageUrl", safe(user.getProfileImageUrl())
                ),
                "notices", petNoticeRepository.findByAuthorOrderByCreatedAtDesc(user).stream().map(this::toNoticeSummaryMap).toList(),
                "communityPosts", communityPostRepository.findByAuthorIdAndStatusNotOrderByCreatedAtDesc(user.getId(), CommunityPostStatus.DELETED)
                        .stream().map(this::toCommunitySummaryMap).toList(),
                "reports", reports.stream().map(this::toReportSummaryMap).toList()
        );
    }

    @Transactional
    public Map<String, Object> updateUserRole(String userId, String roleValue) {
        User user = getUser(userId);
        adminSecurityService.require(AdminPermission.ADMIN_PROMOTE);
        Map<String, Object> before = Map.of("role", user.getRole().name());
        UserRole nextRole = parseRole(roleValue);
        user.setRole(nextRole);
        if (nextRole == UserRole.ADMIN && !UserRole.ADMIN.name().equals(before.get("role"))) {
            user.setAdminEmailVerificationRequired(true);
            user.setAdminEmailVerifiedAt(null);
            user.setAdminEmailVerificationSentAt(null);
        }
        User saved = userRepository.save(user);
        if (saved.getRole() == UserRole.ADMIN) {
            adminPermissionService.ensureDefaults(saved);
            if (saved.isAdminEmailVerificationRequired()) {
                forceAdminEmailReverification(saved);
            }
        }
        Map<String, Object> after = Map.of("role", saved.getRole().name());
        adminAuditService.log("ADMIN_USER_ROLE_UPDATED", "USER", saved.getId().toString(), before, after, Map.of("email", saved.getEmail()));
        return Map.of("id", saved.getId(), "role", saved.getRole().name());
    }

    private void forceAdminEmailReverification(User user) {
        if (user.getFirebaseUid() == null || user.getFirebaseUid().isBlank()) {
            return;
        }
        try {
            firebaseAuth.updateUser(new UserRecord.UpdateRequest(user.getFirebaseUid()).setEmailVerified(false));
        } catch (FirebaseAuthException e) {
            log.warn("관리자 이메일 재인증 준비 실패. 승격은 유지됩니다. userId={}, firebaseUid={}, reason={}",
                    user.getId(),
                    user.getFirebaseUid(),
                    e.getMessage());
        }
    }

    @Transactional
    public Map<String, Object> updateUserStatus(String userId, String statusValue) {
        User user = getUser(userId);
        adminSecurityService.require(AdminPermission.USER_SUSPEND);
        Map<String, Object> before = Map.of("status", normalizeUserStatus(user.getStatus()));
        user.setStatus(parseAdminUserStatus(statusValue));
        User saved = userRepository.save(user);
        Map<String, Object> after = Map.of("status", normalizeUserStatus(saved.getStatus()));
        adminAuditService.log("ADMIN_USER_STATUS_UPDATED", "USER", saved.getId().toString(), before, after, Map.of("email", saved.getEmail()));
        return Map.of("id", saved.getId(), "status", normalizeUserStatus(saved.getStatus()));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listNotices(String query, String region, String breed, String statusValue, String fromValue, String toValue,
                                           Integer page, Integer pageSize, String sortBy, String sortOrder) {
        adminSecurityService.require(AdminPermission.AUDIT_READ);
        int resolvedPage = Math.max((page == null ? 1 : page) - 1, 0);
        int resolvedPageSize = Math.min(Math.max(pageSize == null ? 20 : pageSize, 1), 100);
        Instant from = blank(fromValue) ? null : parseInstant(fromValue);
        Instant to = blank(toValue) ? null : parseInstant(toValue);

        List<Map<String, Object>> items = petNoticeRepository.findAll().stream()
                .filter(notice -> region == null || region.isBlank() || region.equalsIgnoreCase(safe(notice.getMissingRegion())))
                .filter(notice -> breed == null || breed.isBlank() || breed.equalsIgnoreCase(safe(notice.getBreed())))
                .filter(notice -> matchesNoticeQuery(notice, query))
                .filter(notice -> from == null || !notice.getCreatedAt().isBefore(from))
                .filter(notice -> to == null || !notice.getCreatedAt().isAfter(to))
                .filter(notice -> statusValue == null || statusValue.isBlank() || normalizeNoticeStatus(notice).equalsIgnoreCase(statusValue))
                .sorted(noticeComparator(sortBy, sortOrder))
                .map(this::toNoticeSummaryMap)
                .toList();

        return paginate(items, resolvedPage, resolvedPageSize);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> noticeDetail(String noticeId) {
        adminSecurityService.require(AdminPermission.AUDIT_READ);
        PetNotice notice = getNotice(noticeId);
        return toNoticeDetailMap(notice);
    }

    @Transactional
    public Map<String, Object> updateNotice(String noticeId, Map<String, Object> request) {
        adminSecurityService.require(AdminPermission.NOTICE_WRITE);
        PetNotice notice = getNotice(noticeId);
        Map<String, Object> before = toNoticeDetailMap(notice);
        applyNoticeUpdates(notice, request);
        PetNotice saved = petNoticeRepository.save(notice);
        Map<String, Object> after = toNoticeDetailMap(saved);
        adminAuditService.log("ADMIN_NOTICE_UPDATED", "PET_NOTICE", saved.getId().toString(), before, after, null);
        return after;
    }

    @Transactional
    public Map<String, Object> hideNotice(String noticeId) {
        adminSecurityService.require(AdminPermission.NOTICE_WRITE);
        return mapOfNullable(adminService.updateMissingPostVisibility(noticeId, "HIDDEN"));
    }

    @Transactional
    public Map<String, Object> restoreNotice(String noticeId) {
        adminSecurityService.require(AdminPermission.NOTICE_WRITE);
        return mapOfNullable(adminService.updateMissingPostVisibility(noticeId, "VISIBLE"));
    }

    @Transactional
    public Map<String, Object> deleteNotice(String noticeId) {
        adminSecurityService.require(AdminPermission.NOTICE_WRITE);
        return mapOfNullable(adminService.deleteMissingPost(noticeId));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listCommunityPosts(String query, String category, String statusValue, Integer page, Integer pageSize,
                                                  String sortBy, String sortOrder) {
        adminSecurityService.require(AdminPermission.AUDIT_READ);
        int resolvedPage = Math.max((page == null ? 1 : page) - 1, 0);
        int resolvedPageSize = Math.min(Math.max(pageSize == null ? 20 : pageSize, 1), 100);
        CommunityPostStatus status = parseNullableCommunityStatus(statusValue);
        List<Map<String, Object>> items = communityPostRepository.findAll().stream()
                .filter(post -> status == null || post.getStatus() == status)
                .filter(post -> category == null || category.isBlank() || category.equalsIgnoreCase(safe(post.getCategory())))
                .filter(post -> matchesCommunityQuery(post, query))
                .sorted(communityComparator(sortBy, sortOrder))
                .map(this::toCommunitySummaryMap)
                .toList();
        return paginate(items, resolvedPage, resolvedPageSize);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> communityDetail(String postId) {
        adminSecurityService.require(AdminPermission.AUDIT_READ);
        CommunityPost post = getCommunityPost(postId);
        List<CommunityComment> comments = communityCommentRepository.findByPostAndStatusOrderByCreatedAtAsc(post, CommunityCommentStatus.NORMAL);
        List<CommunityPostVote> votes = communityPostVoteRepository.findByPost(post);
        List<CommunityPostReaction> reactions = communityPostReactionRepository.findByPost(post);
        long likeCount = communityPostReactionRepository.countByPostAndReactionType(post, "LIKE");
        Map<String, Object> response = new LinkedHashMap<>(toCommunitySummaryMap(post));
        response.put("likes", likeCount);
        response.put("content", post.getContent());
        response.put("tags", Optional.ofNullable(post.getTags())
                .orElseGet(List::of)
                .stream()
                .filter(Objects::nonNull)
                .toList());
        response.put("images", Optional.ofNullable(post.getImages())
                .orElseGet(List::of)
                .stream()
                .filter(Objects::nonNull)
                .map(image -> image.getImageUrl())
                .filter(Objects::nonNull)
                .toList());
        response.put("poll", buildAdminPollMap(post));
        response.put("votes", buildAdminVotesMap(votes));
        response.put("reactions", buildAdminReactionsMap(reactions));
        response.put("comments", buildAdminCommentsMap(comments));
        return response;
    }

    @Transactional
    public Map<String, Object> deleteCommunityPost(String postId) {
        adminSecurityService.require(AdminPermission.NOTICE_WRITE);
        return mapOfNullable(adminService.deleteCommunityPost(postId));
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Map<String, Object> sendNotification(AdminNotificationSendRequest request) {
        adminSecurityService.require(AdminPermission.NOTIFICATION_SEND);
        User actor = adminSecurityService.getCurrentAdminUser();
        List<User> recipients = resolveNotificationRecipients(request);
        int deliveredCount = 0; // token-level delivered count
        int deliveredUserCount = 0; // recipient-level delivered count
        int failedCount = 0;
        int failedTokenCount = 0;
        int skippedCount = 0;
        for (User recipient : recipients) {
            try {
                Map<String, Object> response = notificationService.createAndSendNotification(
                        recipient,
                        actor,
                        NotificationType.ADMIN_BROADCAST,
                        NotificationTargetType.ADMIN_BROADCAST,
                        recipient.getId(),
                        request.getTitle(),
                        request.getBody(),
                        NotificationPriority.HIGH,
                        null,
                        Map.of("target", request.getTarget())
                );
                int sentCount = ((Number) response.getOrDefault("sentCount", 0)).intValue();
                int recipientFailedTokenCount = ((Number) response.getOrDefault("failedTokenCount", 0)).intValue();
                deliveredCount += sentCount;
                failedTokenCount += recipientFailedTokenCount;
                if (sentCount > 0) {
                    deliveredUserCount++;
                } else if (Boolean.TRUE.equals(response.get("skipped"))) {
                    skippedCount++;
                } else {
                    // Token-level failures are tracked separately in failedTokenCount.
                    // User-level failedCount is reserved for actual server-side exceptions.
                    skippedCount++;
                }
            } catch (Exception e) {
                failedCount++;
                log.warn("admin broadcast notification failed. recipientId={}, actorId={}, reason={}",
                        recipient.getId(),
                        actor == null ? null : actor.getId(),
                        e.getMessage());
            }
        }

        String dispatchStatus;
        if (failedCount > 0 && deliveredCount == 0) {
            dispatchStatus = "FAILED";
        } else if (deliveredCount == 0 && skippedCount > 0 && failedCount == 0) {
            dispatchStatus = "SKIPPED";
        } else if (failedCount > 0 || skippedCount > 0 || failedTokenCount > 0) {
            dispatchStatus = "PARTIAL";
        } else {
            dispatchStatus = "SENT";
        }

        AdminNotificationDispatch dispatch = adminNotificationDispatchRepository.save(AdminNotificationDispatch.builder()
                .actorUser(actor)
                .targetKind(request.getTarget())
                .title(request.getTitle())
                .body(request.getBody())
                .status(dispatchStatus)
                .targetCount(recipients.size())
                .deliveredCount(deliveredCount)
                .failedCount(failedCount)
                .metadata(writeJson(orderedMap(
                        "target", request.getTarget(),
                        "userIds", request.getUserIds() == null ? List.of() : request.getUserIds(),
                        "deliveredUserCount", deliveredUserCount,
                        "skippedCount", skippedCount,
                        "failedTokenCount", failedTokenCount
                )))
                .build());
        Map<String, Object> response = toDispatchMap(dispatch);
        response.put("targetCount", recipients.size());
        response.put("deliveredUserCount", deliveredUserCount);
        response.put("skippedCount", skippedCount);
        response.put("failedTokenCount", failedTokenCount);
        try {
            adminAuditService.log("ADMIN_NOTIFICATION_SENT", "NOTIFICATION_DISPATCH", dispatch.getId().toString(), null, response, null);
        } catch (RuntimeException ignored) {
            // Audit failure should not break notification API response.
        }
        return response;
    }

    private Map<String, Object> buildAdminPollMap(CommunityPost post) {
        String pollQuestion = post.getPollQuestion();
        List<String> pollOptions = Optional.ofNullable(post.getPollOptions())
                .orElseGet(List::of)
                .stream()
                .filter(Objects::nonNull)
                .toList();
        if ((pollQuestion == null || pollQuestion.isBlank()) && pollOptions.isEmpty()) {
            return null;
        }

        List<Map<String, Object>> options = pollOptions.stream()
                .map(option -> orderedMap(
                        "option", option,
                        "voteCount", communityPostVoteRepository.countByPostAndSelectedOption(post, option)
                ))
                .toList();
        long totalVotes = options.stream()
                .mapToLong(option -> ((Number) option.get("voteCount")).longValue())
                .sum();

        return orderedMap(
                "question", pollQuestion,
                "options", options,
                "totalVotes", totalVotes
        );
    }

    private Map<String, Object> buildAdminVotesMap(List<CommunityPostVote> votes) {
        Map<String, Long> countByOption = votes.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(CommunityPostVote::getSelectedOption, LinkedHashMap::new, Collectors.counting()));
        List<Map<String, Object>> voters = votes.stream()
                .filter(Objects::nonNull)
                .map(vote -> orderedMap(
                        "voteId", vote.getId(),
                        "userId", vote.getUser() == null ? null : vote.getUser().getId(),
                        "userName", vote.getUser() == null ? "알 수 없음" : safe(vote.getUser().getNickname()),
                        "selectedOption", vote.getSelectedOption(),
                        "createdAt", vote.getCreatedAt(),
                        "updatedAt", vote.getUpdatedAt()
                ))
                .toList();
        return orderedMap(
                "total", votes.size(),
                "countByOption", countByOption,
                "voters", voters
        );
    }

    private Map<String, Object> buildAdminReactionsMap(List<CommunityPostReaction> reactions) {
        Map<String, Long> countByType = reactions.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(CommunityPostReaction::getReactionType, LinkedHashMap::new, Collectors.counting()));
        List<Map<String, Object>> reactors = reactions.stream()
                .filter(Objects::nonNull)
                .map(reaction -> orderedMap(
                        "reactionId", reaction.getId(),
                        "userId", reaction.getUser() == null ? null : reaction.getUser().getId(),
                        "userName", reaction.getUser() == null ? "알 수 없음" : safe(reaction.getUser().getNickname()),
                        "reactionType", reaction.getReactionType(),
                        "createdAt", reaction.getCreatedAt(),
                        "updatedAt", reaction.getUpdatedAt()
                ))
                .toList();
        return orderedMap(
                "total", reactions.size(),
                "countByType", countByType,
                "reactors", reactors
        );
    }

    private List<Map<String, Object>> buildAdminCommentsMap(List<CommunityComment> comments) {
        List<Map<String, Object>> rootComments = new ArrayList<>();
        Map<UUID, Map<String, Object>> byId = new LinkedHashMap<>();
        for (CommunityComment comment : comments) {
            Map<String, Object> row = orderedMap(
                    "id", comment.getId(),
                    "authorId", comment.getAuthor() == null ? null : comment.getAuthor().getId(),
                    "authorName", comment.getAuthor() == null ? "알 수 없음" : safe(comment.getAuthor().getNickname()),
                    "content", comment.getContent(),
                    "status", comment.getStatus() == null ? "UNKNOWN" : comment.getStatus().name(),
                    "createdAt", comment.getCreatedAt(),
                    "updatedAt", comment.getUpdatedAt(),
                    "parentCommentId", comment.getParentComment() == null ? null : comment.getParentComment().getId(),
                    "replies", new ArrayList<Map<String, Object>>()
            );
            byId.put(comment.getId(), row);
        }
        for (Map<String, Object> row : byId.values()) {
            UUID parentId = (UUID) row.get("parentCommentId");
            if (parentId == null || !byId.containsKey(parentId)) {
                rootComments.add(row);
                continue;
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> replies = (List<Map<String, Object>>) byId.get(parentId).get("replies");
            replies.add(row);
        }
        return rootComments;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> notificationHistory(Integer page, Integer pageSize) {
        adminSecurityService.require(AdminPermission.NOTIFICATION_SEND);
        int resolvedPage = Math.max((page == null ? 1 : page) - 1, 0);
        int resolvedPageSize = Math.min(Math.max(pageSize == null ? 20 : pageSize, 1), 100);
        var history = adminNotificationDispatchRepository.findAllByOrderByCreatedAtDesc(
                PageRequest.of(resolvedPage, resolvedPageSize, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        return orderedMap(
                "items", history.getContent().stream().map(this::toDispatchMap).toList(),
                "page", resolvedPage + 1,
                "pageSize", resolvedPageSize,
                "total", history.getTotalElements()
        );
    }

    @Transactional
    public List<Map<String, Object>> integrationOverview(boolean forceRefresh) {
        adminSecurityService.require(AdminPermission.INTEGRATION_MANAGE);
        if (forceRefresh || adminIntegrationStatusRepository.count() == 0L) {
            refreshAllIntegrations();
        }
        return List.of("DATABASE", "REDIS", "FIREBASE", "SHELTER_API").stream()
                .map(this::latestIntegrationSnapshot)
                .filter(map -> !map.isEmpty())
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> servicesOverview(boolean forceRefresh) {
        List<Map<String, Object>> services = integrationOverview(forceRefresh);
        return orderedMap(
                "globalStatus", latestGlobalStatus(),
                "services", services
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> integrationDetail(String integrationKey) {
        adminSecurityService.require(AdminPermission.INTEGRATION_MANAGE);
        String normalized = integrationKey.toUpperCase(Locale.ROOT);
        Map<String, Object> latest = latestIntegrationSnapshot(normalized);
        List<Map<String, Object>> logs = adminIntegrationStatusRepository.findTop20ByIntegrationKeyOrderByCreatedAtDesc(normalized)
                .stream()
                .map(this::toIntegrationMap)
                .toList();
        return orderedMap(
                "id", normalized,
                "name", normalized,
                "status", latest.getOrDefault("status", "UNKNOWN"),
                "uptime", latest.getOrDefault("uptime", "n/a"),
                "latency", latest.getOrDefault("latency", null),
                "cluster", "default",
                "telemetry", orderedMap(
                        "lastCheckedAt", latest.getOrDefault("checkedAt", null),
                        "message", latest.getOrDefault("message", "")
                ),
                "recentLogs", logs,
                "latest", latest,
                "logs", logs
        );
    }

    @Transactional
    public Map<String, Object> recheckIntegration(String integrationKey) {
        adminSecurityService.require(AdminPermission.INTEGRATION_MANAGE);
        String normalized = integrationKey.toUpperCase(Locale.ROOT);
        Map<String, Object> result = switch (normalized) {
            case "DATABASE" -> toIntegrationMap(checkDatabase());
            case "REDIS" -> toIntegrationMap(checkRedis());
            case "FIREBASE" -> toIntegrationMap(checkFirebase());
            case "SHELTER_API" -> toIntegrationMap(checkShelterApiConfiguration());
            case "GLOBAL", "ALL" -> {
                refreshAllIntegrations();
                yield Map.of("updated", true);
            }
            default -> throw ApiException.badRequest("INVALID_INTEGRATION_KEY", "지원하지 않는 integration key입니다.");
        };
        adminAuditService.log("ADMIN_INTEGRATION_RECHECKED", "INTEGRATION", normalized, null, result, null);
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> auditLogs(String query, Integer page, Integer pageSize) {
        adminSecurityService.require(AdminPermission.AUDIT_READ);
        int resolvedPage = Math.max((page == null ? 1 : page) - 1, 0);
        int resolvedPageSize = Math.min(Math.max(pageSize == null ? 20 : pageSize, 1), 100);
        var logs = blank(query)
                ? adminAuditLogRepository.findAll(PageRequest.of(resolvedPage, resolvedPageSize, Sort.by(Sort.Direction.DESC, "createdAt")))
                : adminAuditLogRepository.findByActionContainingIgnoreCaseOrTargetTypeContainingIgnoreCase(
                query.trim(), query.trim(), PageRequest.of(resolvedPage, resolvedPageSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        return orderedMap(
                "items", logs.getContent().stream().map(log -> orderedMap(
                        "id", log.getId(),
                        "action", log.getAction(),
                        "targetType", log.getTargetType(),
                        "targetId", log.getTargetId(),
                        "ipAddress", safe(log.getIpAddress()),
                        "timestamp", log.getCreatedAt(),
                        "adminName", log.getActorUser() == null ? null : log.getActorUser().getNickname()
                )).toList(),
                "page", resolvedPage + 1,
                "pageSize", resolvedPageSize,
                "total", logs.getTotalElements()
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> auditLogDetail(String logId) {
        adminSecurityService.require(AdminPermission.AUDIT_READ);
        UUID id = parseUuid(logId, "INVALID_AUDIT_LOG_ID", "올바르지 않은 감사 로그 ID 형식입니다.");
        var log = adminAuditLogRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("AUDIT_LOG_NOT_FOUND", "감사 로그를 찾을 수 없습니다."));
        return orderedMap(
                "id", log.getId(),
                "action", log.getAction(),
                "targetType", log.getTargetType(),
                "targetId", log.getTargetId(),
                "adminName", log.getActorUser() == null ? null : log.getActorUser().getNickname(),
                "ipAddress", safe(log.getIpAddress()),
                "timestamp", log.getCreatedAt(),
                "before", readJsonValue(log.getBeforeState()),
                "after", readJsonValue(log.getAfterState()),
                "metadata", readJsonValue(log.getMetadata())
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> settings() {
        adminSecurityService.require(AdminPermission.SETTINGS_MANAGE);
        String defaultApiEndpoint = "https://apis.data.go.kr/1543061/abandonmentPublicService_v2";
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("siteName", settingValue("siteName", "Pogun"));
        values.put("supportEmail", settingValue("supportEmail", "support@paw.gbsw.hs.kr"));
        values.put("defaultLanguage", settingValue("defaultLanguage", "ko"));
        values.put("logoUrl", settingValue("logoUrl", ""));
        values.put("apiEndpoint", settingValue("apiEndpoint", defaultApiEndpoint));
        values.put("autoSyncEnabled", settingBoolean("autoSyncEnabled", true));
        values.put("globalPushEnabled", settingBoolean("globalPushEnabled", true));
        values.put("fcmServerKeyMasked", maskSecret(settingValue("fcmServerKey", "")));
        values.put("firebaseProjectId", safe(firebaseAuthProperties.getProjectId()));
        values.put("fcmConfigured", firebaseAuth != null);
        values.put("shelterApiConfigured", settingValue("apiEndpoint", defaultApiEndpoint).startsWith("http"));
        return values;
    }

    @Transactional
    public Map<String, Object> updateSettings(Map<String, Object> request) {
        adminSecurityService.require(AdminPermission.SETTINGS_MANAGE);
        User actor = adminSecurityService.getCurrentAdminUser();
        Map<String, Object> before = settings();
        Set<String> allowedKeys = Set.of("siteName", "supportEmail", "defaultLanguage", "logoUrl", "apiEndpoint", "autoSyncEnabled", "globalPushEnabled", "fcmServerKey");
        Set<String> readOnlyKeys = Set.of("fcmServerKeyMasked", "firebaseProjectId", "fcmConfigured", "shelterApiConfigured");
        request.forEach((key, value) -> {
            if (readOnlyKeys.contains(key)) {
                return;
            }
            if (!allowedKeys.contains(key)) {
                throw ApiException.badRequest("INVALID_SETTING_KEY", "지원하지 않는 설정 키입니다: " + key);
            }
            AdminSetting setting = adminSettingRepository.findBySettingKey(key)
                    .orElseGet(() -> AdminSetting.builder().settingKey(key).build());
            setting.setSettingValue(String.valueOf(value));
            setting.setValueType(value instanceof Boolean ? "BOOLEAN" : "STRING");
            setting.setUpdatedByUser(actor);
            adminSettingRepository.save(setting);
        });
        Map<String, Object> after = settings();
        adminAuditService.log("ADMIN_SETTINGS_UPDATED", "ADMIN_SETTINGS", "global", before, after, null);
        return after;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> referenceDataSummary() {
        adminSecurityService.require(AdminPermission.AUDIT_READ);
        Set<String> animalTypes = petNoticeRepository.findAll().stream()
                .map(PetNotice::getAnimalType)
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> regions = petNoticeRepository.findAll().stream()
                .map(PetNotice::getMissingRegion)
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return orderedMap(
                "animalTypesCount", animalTypes.size(),
                "regionCodesCount", regions.size(),
                "noticeStatusLabelsCount", 5,
                "reportStatusLabelsCount", 4
        );
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> referenceDataList(String kind) {
        adminSecurityService.require(AdminPermission.SETTINGS_MANAGE);
        String resolvedKind = normalizeReferenceKind(kind);
        return adminReferenceDataRepository.findByDataKindOrderByDataLabelAsc(resolvedKind).stream()
                .map(item -> orderedMap(
                        "id", item.getId(),
                        "kind", item.getDataKind(),
                        "key", item.getDataKey(),
                        "label", item.getDataLabel(),
                        "active", item.isActive(),
                        "metadata", readJsonValue(item.getMetadata()),
                        "updatedAt", item.getUpdatedAt()
                ))
                .toList();
    }

    @Transactional
    public Map<String, Object> createReferenceData(String kind, Map<String, Object> request) {
        adminSecurityService.require(AdminPermission.SETTINGS_MANAGE);
        User actor = adminSecurityService.getCurrentAdminUser();
        String resolvedKind = normalizeReferenceKind(kind);
        String key = nullableString(request.get("key"));
        String label = nullableString(request.get("label"));
        if (blank(key) || blank(label)) {
            throw ApiException.badRequest("INVALID_REFERENCE_DATA", "key와 label은 필수입니다.");
        }
        AdminReferenceData saved = adminReferenceDataRepository.save(AdminReferenceData.builder()
                .dataKind(resolvedKind)
                .dataKey(key)
                .dataLabel(label)
                .active(Boolean.parseBoolean(String.valueOf(request.getOrDefault("active", true))))
                .metadata(writeJson(request.get("metadata")))
                .updatedByUser(actor)
                .build());
        return orderedMap("id", saved.getId(), "kind", saved.getDataKind(), "key", saved.getDataKey(), "label", saved.getDataLabel(), "active", saved.isActive());
    }

    @Transactional
    public Map<String, Object> updateReferenceData(String kind, String id, Map<String, Object> request) {
        adminSecurityService.require(AdminPermission.SETTINGS_MANAGE);
        User actor = adminSecurityService.getCurrentAdminUser();
        String resolvedKind = normalizeReferenceKind(kind);
        UUID uuid = parseUuid(id, "INVALID_REFERENCE_DATA_ID", "올바르지 않은 기준 데이터 ID 형식입니다.");
        AdminReferenceData target = adminReferenceDataRepository.findByIdAndDataKind(uuid, resolvedKind)
                .orElseThrow(() -> ApiException.notFound("REFERENCE_DATA_NOT_FOUND", "기준 데이터를 찾을 수 없습니다."));
        if (request.containsKey("key")) target.setDataKey(nullableString(request.get("key")));
        if (request.containsKey("label")) target.setDataLabel(nullableString(request.get("label")));
        if (request.containsKey("active")) target.setActive(Boolean.parseBoolean(String.valueOf(request.get("active"))));
        if (request.containsKey("metadata")) target.setMetadata(writeJson(request.get("metadata")));
        target.setUpdatedByUser(actor);
        AdminReferenceData saved = adminReferenceDataRepository.save(target);
        return orderedMap("id", saved.getId(), "kind", saved.getDataKind(), "key", saved.getDataKey(), "label", saved.getDataLabel(), "active", saved.isActive());
    }

    @Transactional
    public void deleteReferenceData(String kind, String id) {
        adminSecurityService.require(AdminPermission.SETTINGS_MANAGE);
        String resolvedKind = normalizeReferenceKind(kind);
        UUID uuid = parseUuid(id, "INVALID_REFERENCE_DATA_ID", "올바르지 않은 기준 데이터 ID 형식입니다.");
        AdminReferenceData target = adminReferenceDataRepository.findByIdAndDataKind(uuid, resolvedKind)
                .orElseThrow(() -> ApiException.notFound("REFERENCE_DATA_NOT_FOUND", "기준 데이터를 찾을 수 없습니다."));
        adminReferenceDataRepository.delete(target);
    }

    private void refreshAllIntegrations() {
        checkDatabase();
        checkRedis();
        checkFirebase();
        checkShelterApiConfiguration();
    }

    private AdminIntegrationStatus checkDatabase() {
        long startedAt = System.currentTimeMillis();
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("select 1");
            return adminIntegrationStatusRepository.save(AdminIntegrationStatus.builder()
                    .integrationKey("DATABASE")
                    .status("OPERATIONAL")
                    .latencyMs(System.currentTimeMillis() - startedAt)
                    .message("DB 연결 정상")
                    .build());
        } catch (Exception e) {
            return adminIntegrationStatusRepository.save(AdminIntegrationStatus.builder()
                    .integrationKey("DATABASE")
                    .status("OUTAGE")
                    .latencyMs(System.currentTimeMillis() - startedAt)
                    .message("DB 연결 실패")
                    .details(safe(e.getMessage()))
                    .build());
        }
    }

    private AdminIntegrationStatus checkRedis() {
        long startedAt = System.currentTimeMillis();
        try (var connection = redisConnectionFactory.getConnection()) {
            String pong = connection.ping();
            return adminIntegrationStatusRepository.save(AdminIntegrationStatus.builder()
                    .integrationKey("REDIS")
                    .status("PONG".equalsIgnoreCase(pong) ? "OPERATIONAL" : "DEGRADED")
                    .latencyMs(System.currentTimeMillis() - startedAt)
                    .message("Redis ping=" + pong)
                    .build());
        } catch (Exception e) {
            return adminIntegrationStatusRepository.save(AdminIntegrationStatus.builder()
                    .integrationKey("REDIS")
                    .status("OUTAGE")
                    .latencyMs(System.currentTimeMillis() - startedAt)
                    .message("Redis 연결 실패")
                    .details(safe(e.getMessage()))
                    .build());
        }
    }

    private AdminIntegrationStatus checkFirebase() {
        long startedAt = System.currentTimeMillis();
        try {
            firebaseAuth.listUsers(null).iterateAll().iterator().hasNext();
            return adminIntegrationStatusRepository.save(AdminIntegrationStatus.builder()
                    .integrationKey("FIREBASE")
                    .status("OPERATIONAL")
                    .latencyMs(System.currentTimeMillis() - startedAt)
                    .message("Firebase Auth 연결 정상")
                    .build());
        } catch (Exception e) {
            return adminIntegrationStatusRepository.save(AdminIntegrationStatus.builder()
                    .integrationKey("FIREBASE")
                    .status("OUTAGE")
                    .latencyMs(System.currentTimeMillis() - startedAt)
                    .message("Firebase Auth 연결 실패")
                    .details(safe(e.getMessage()))
                    .build());
        }
    }

    private AdminIntegrationStatus checkShelterApiConfiguration() {
        long startedAt = System.currentTimeMillis();
        String endpoint = settingValue("apiEndpoint", "https://apis.data.go.kr/1543061/abandonmentPublicService_v2");
        if (StringUtils.hasText(endpoint) && endpoint.startsWith("http")) {
            return adminIntegrationStatusRepository.save(AdminIntegrationStatus.builder()
                    .integrationKey("SHELTER_API")
                    .status("OPERATIONAL")
                    .latencyMs(System.currentTimeMillis() - startedAt)
                    .message("외부 보호소 API 설정 정상")
                    .build());
        }
        return adminIntegrationStatusRepository.save(AdminIntegrationStatus.builder()
                .integrationKey("SHELTER_API")
                .status("DISABLED")
                .latencyMs(System.currentTimeMillis() - startedAt)
                .message("외부 보호소 API 엔드포인트 미설정")
                .build());
    }

    private Map<String, Object> latestIntegrationSnapshot(String integrationKey) {
        return adminIntegrationStatusRepository.findTopByIntegrationKeyOrderByCreatedAtDesc(integrationKey)
                .map(this::toIntegrationMap)
                .orElse(Map.of());
    }

    private String latestGlobalStatus() {
        Collection<Map<String, Object>> snapshots = List.of(
                latestIntegrationSnapshot("DATABASE"),
                latestIntegrationSnapshot("REDIS"),
                latestIntegrationSnapshot("FIREBASE"),
                latestIntegrationSnapshot("SHELTER_API")
        );
        if (snapshots.stream().anyMatch(map -> "OUTAGE".equals(map.get("status")))) {
            return "OUTAGE";
        }
        if (snapshots.stream().anyMatch(map -> "DEGRADED".equals(map.get("status")))) {
            return "DEGRADED";
        }
        return "OPERATIONAL";
    }

    private Map<String, Object> toIntegrationMap(AdminIntegrationStatus status) {
        return orderedMap(
                "id", status.getId(),
                "name", status.getIntegrationKey(),
                "status", status.getStatus(),
                "uptime", "n/a",
                "latency", status.getLatencyMs(),
                "message", safe(status.getMessage()),
                "details", readJsonValue(status.getDetails()),
                "checkedAt", status.getCreatedAt()
        );
    }

    private Map<String, Object> kpiForRange(LocalDate fromDate, LocalDate toDate) {
        ZoneId zone = ZoneId.systemDefault();
        Instant from = fromDate.atStartOfDay(zone).toInstant();
        Instant to = toDate.plusDays(1).atStartOfDay(zone).toInstant();
        long reportNotices = reportRepository.countByTargetTypeAndCreatedAtBetween(ReportTargetType.PET_NOTICE, from, to);
        long resolvedNotices = petNoticeRepository.countByStatusAndUpdatedAtBetween(PetNoticeStatus.RESOLVED, from, to);
        long reports = reportRepository.countByCreatedAtBetween(from, to);
        long signups = userRepository.countByCreatedAtBetween(from, to);
        return orderedMap(
                "reportedNotices", reportNotices,
                "resolvedNotices", resolvedNotices,
                "reports", reports,
                "users", signups,
                "newUsers", signups
        );
    }

    private String normalizeReferenceKind(String kind) {
        if (blank(kind)) {
            throw ApiException.badRequest("INVALID_REFERENCE_KIND", "reference kind는 필수입니다.");
        }
        return switch (kind.trim().toLowerCase(Locale.ROOT)) {
            case "animal-types", "animal_types", "animaltypes" -> "ANIMAL_TYPES";
            case "region-codes", "region_codes", "regioncodes" -> "REGION_CODES";
            case "notice-status-labels", "notice_status_labels", "noticestatuslabels" -> "NOTICE_STATUS_LABELS";
            default -> throw ApiException.badRequest("INVALID_REFERENCE_KIND", "지원하지 않는 reference kind입니다.");
        };
    }

    private String maskSecret(String value) {
        if (blank(value)) {
            return "";
        }
        if (value.length() <= 8) {
            return "****";
        }
        return value.substring(0, 4) + "..." + value.substring(value.length() - 4);
    }

    private List<User> resolveNotificationRecipients(AdminNotificationSendRequest request) {
        String target = request.getTarget().trim().toLowerCase(Locale.ROOT);
        return switch (target) {
            case "all" -> userRepository.findAll().stream().filter(user -> user.getStatus() == UserStatus.ACTIVE).toList();
            case "active" -> resolveActiveUsers();
            case "specific" -> {
                if (request.getUserIds() == null || request.getUserIds().isEmpty()) {
                    throw ApiException.badRequest("MISSING_NOTIFICATION_USERS", "specific 발송에는 userIds가 필요합니다.");
                }
                yield request.getUserIds().stream()
                        .map(id -> getUser(id))
                        .distinct()
                        .toList();
            }
            default -> throw ApiException.badRequest("INVALID_NOTIFICATION_TARGET", "지원하지 않는 알림 대상입니다.");
        };
    }

    private List<User> resolveActiveUsers() {
        return userRepository.findAll().stream()
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .filter(this::hasUserAppSession)
                .filter(user -> userPresenceService.snapshot(user).online())
                .toList();
    }

    private List<User> resolveActiveUsersSafely() {
        try {
            return resolveActiveUsers();
        } catch (RuntimeException ignored) {
            return userRepository.findAll().stream()
                    .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                    .limit(1000)
                    .toList();
        }
    }

    private long resolveConnectedUsersSafely() {
        try {
            return resolveActiveUsers().size();
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }

    private boolean hasUserAppSession(User user) {
        if (user == null || user.getFirebaseUid() == null || user.getFirebaseUid().isBlank()) {
            return false;
        }
        Map<String, Instant> sessions = presenceSessionStore.getGlobalSessions(user.getFirebaseUid());
        if (sessions.isEmpty()) {
            return false;
        }
        for (String sessionKey : sessions.keySet()) {
            if (sessionKey == null || sessionKey.isBlank()) {
                continue;
            }
            String normalized = sessionKey.trim().toLowerCase(Locale.ROOT);
            if (normalized.startsWith(USER_APP_DOMAIN + ":")) {
                return true;
            }
        }
        return false;
    }

    private Instant resolveFrom(String fromValue) {
        ZoneId zoneId = ZoneId.systemDefault();
        return blank(fromValue)
                ? LocalDate.now(zoneId).minusDays(6).atStartOfDay(zoneId).toInstant()
                : parseDateBoundary(fromValue, true);
    }

    private Instant resolveTo(String toValue) {
        ZoneId zoneId = ZoneId.systemDefault();
        return blank(toValue)
                ? LocalDate.now(zoneId).plusDays(1).atStartOfDay(zoneId).toInstant()
                : parseDateBoundary(toValue, false);
    }

    private LocalDate resolveDate(String value, LocalDate defaultValue) {
        if (blank(value)) {
            return defaultValue;
        }
        return LocalDate.parse(value.trim());
    }

    private Instant parseDateBoundary(String value, boolean start) {
        ZoneId zoneId = ZoneId.systemDefault();
        LocalDate date = LocalDate.parse(value.trim());
        return start ? date.atStartOfDay(zoneId).toInstant() : date.plusDays(1).atStartOfDay(zoneId).toInstant();
    }

    private String resolveUserSortProperty(String sortBy) {
        return switch (blank(sortBy) ? "createdAt" : sortBy.trim()) {
            case "email" -> "email";
            case "lastLoginAt", "lastActiveAt" -> "lastActiveAt";
            default -> "createdAt";
        };
    }

    private Comparator<PetNotice> noticeComparator(String sortBy, String sortOrder) {
        Comparator<PetNotice> comparator = switch (blank(sortBy) ? "createdAt" : sortBy.trim()) {
            case "missingDate" -> Comparator.comparing(PetNotice::getMissingDate, Comparator.nullsLast(Instant::compareTo));
            case "title" -> Comparator.comparing(PetNotice::getTitle, Comparator.nullsLast(String::compareToIgnoreCase));
            default -> Comparator.comparing(PetNotice::getCreatedAt, Comparator.nullsLast(Instant::compareTo));
        };
        return "asc".equalsIgnoreCase(sortOrder) ? comparator : comparator.reversed();
    }

    private Comparator<CommunityPost> communityComparator(String sortBy, String sortOrder) {
        Comparator<CommunityPost> comparator = switch (blank(sortBy) ? "createdAt" : sortBy.trim()) {
            case "title" -> Comparator.comparing(CommunityPost::getTitle, Comparator.nullsLast(String::compareToIgnoreCase));
            case "likeCount" -> Comparator.comparing(CommunityPost::getLikeCount, Comparator.nullsLast(Long::compareTo));
            default -> Comparator.comparing(CommunityPost::getCreatedAt, Comparator.nullsLast(Instant::compareTo));
        };
        return "asc".equalsIgnoreCase(sortOrder) ? comparator : comparator.reversed();
    }

    private boolean matchesNoticeQuery(PetNotice notice, String query) {
        if (blank(query)) {
            return true;
        }
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        return safe(notice.getTitle()).toLowerCase(Locale.ROOT).contains(normalized)
                || safe(notice.getAnimalType()).toLowerCase(Locale.ROOT).contains(normalized)
                || safe(notice.getBreed()).toLowerCase(Locale.ROOT).contains(normalized);
    }

    private boolean matchesCommunityQuery(CommunityPost post, String query) {
        if (blank(query)) {
            return true;
        }
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        return safe(post.getTitle()).toLowerCase(Locale.ROOT).contains(normalized)
                || safe(post.getContent()).toLowerCase(Locale.ROOT).contains(normalized)
                || safe(post.getAuthor().getNickname()).toLowerCase(Locale.ROOT).contains(normalized);
    }

    private Map<String, Object> paginate(List<Map<String, Object>> items, int page, int pageSize) {
        int fromIndex = Math.min(page * pageSize, items.size());
        int toIndex = Math.min(fromIndex + pageSize, items.size());
        return orderedMap(
                "items", items.subList(fromIndex, toIndex),
                "page", page + 1,
                "pageSize", pageSize,
                "total", items.size()
        );
    }

    private Map<String, Object> toUserSummaryMap(User user) {
        UserPresenceService.PresenceSnapshot snapshot = userPresenceService.snapshot(user);
        String effective = snapshot.availabilityStatus() == null ? "OFFLINE" : snapshot.availabilityStatus().name();
        String connection = snapshot.actualConnectionState();
        Instant lastActiveAt = snapshot.lastActiveAt() != null ? snapshot.lastActiveAt() : user.getLastActiveAt();
        return orderedMap(
            "id", user.getId(),
            "name", safe(user.getNickname()),
            "email", safe(user.getEmail()),
            "role", user.getRole().name(),
            "status", normalizeUserStatus(user.getStatus()),
            "createdAt", user.getCreatedAt(),
            "lastLoginAt", user.getLastActiveAt(),
            "presence", effective,
            "presenceConnectionState", connection,
            "presenceLastActiveAt", lastActiveAt
        );
    }

    private Map<String, Object> toNoticeSummaryMap(PetNotice notice) {
        return orderedMap(
                "id", notice.getId(),
                "title", safe(notice.getTitle()),
                "animalType", safe(notice.getAnimalType()),
                "status", normalizeNoticeStatus(notice),
                "reporter", safe(notice.getAuthor().getNickname()),
                "reportedAt", notice.getCreatedAt(),
                "thumbnailUrl", notice.getImages().stream().map(PetNoticeImage::getImageUrl).findFirst().orElse(null),
                "hidden", notice.getHidden(),
                "region", safe(notice.getMissingRegion())
        );
    }

    private Map<String, Object> toNoticeDetailMap(PetNotice notice) {
        Map<String, Object> response = new LinkedHashMap<>(toNoticeSummaryMap(notice));
        response.put("content", safe(notice.getDescription()));
        response.put("breed", safe(notice.getBreed()));
        response.put("gender", notice.getGender() == null ? null : notice.getGender().name());
        response.put("age", notice.getAge());
        response.put("color", safe(notice.getColor()));
        response.put("location", safe(notice.getMissingAddress()));
        response.put("missingDate", notice.getMissingDate());
        response.put("rewardAmount", notice.getRewardAmount());
        response.put("contactPhone", safe(notice.getContactPhone()));
        response.put("images", notice.getImages().stream().map(PetNoticeImage::getImageUrl).toList());
        response.put("reportCount", reportCount(ReportTargetType.PET_NOTICE, notice.getId()));
        return response;
    }

    private void applyNoticeUpdates(PetNotice notice, Map<String, Object> request) {
        if (request.containsKey("title")) {
            notice.setTitle(String.valueOf(request.get("title")).trim());
        }
        if (request.containsKey("description")) {
            notice.setDescription(nullableString(request.get("description")));
        }
        if (request.containsKey("animalType")) {
            notice.setAnimalType(String.valueOf(request.get("animalType")).trim());
        }
        if (request.containsKey("breed")) {
            notice.setBreed(nullableString(request.get("breed")));
        }
        if (request.containsKey("missingRegion")) {
            notice.setMissingRegion(String.valueOf(request.get("missingRegion")).trim());
        }
        if (request.containsKey("missingAddress")) {
            notice.setMissingAddress(nullableString(request.get("missingAddress")));
        }
        if (request.containsKey("contactPhone")) {
            notice.setContactPhone(nullableString(request.get("contactPhone")));
        }
        if (request.containsKey("rewardAmount")) {
            notice.setRewardAmount(request.get("rewardAmount") == null ? null : Integer.valueOf(String.valueOf(request.get("rewardAmount"))));
        }
        if (request.containsKey("status")) {
            applyAdminNoticeStatus(notice, String.valueOf(request.get("status")));
        }
    }

    private void applyAdminNoticeStatus(PetNotice notice, String statusValue) {
        String normalized = statusValue.trim().toUpperCase(Locale.ROOT);
        switch (normalized) {
            case "HIDDEN" -> notice.setHidden(true);
            case "LOST" -> {
                notice.setHidden(false);
                notice.setStatus(PetNoticeStatus.OPEN);
            }
            case "FOUND" -> {
                notice.setHidden(false);
                notice.setStatus(PetNoticeStatus.CLOSED);
            }
            case "RESOLVED" -> {
                notice.setHidden(false);
                notice.setStatus(PetNoticeStatus.RESOLVED);
            }
            case "REPORTED" -> notice.setHidden(false);
            default -> throw ApiException.badRequest("INVALID_NOTICE_STATUS", "지원하지 않는 공고 상태입니다.");
        }
    }

    private String normalizeNoticeStatus(PetNotice notice) {
        if (Boolean.TRUE.equals(notice.getHidden())) {
            return "HIDDEN";
        }
        if (reportCount(ReportTargetType.PET_NOTICE, notice.getId()) > 0L) {
            return "REPORTED";
        }
        return switch (notice.getStatus()) {
            case RESOLVED -> "RESOLVED";
            case CLOSED -> "FOUND";
            default -> "LOST";
        };
    }

    private Map<String, Object> toCommunitySummaryMap(CommunityPost post) {
        int commentCount = communityCommentRepository.findByPostAndStatusOrderByCreatedAtAsc(post, CommunityCommentStatus.NORMAL).size();
        String authorName = post.getAuthor() == null ? "알 수 없음" : safe(post.getAuthor().getNickname());
        long likeCount = communityPostReactionRepository.countByPostAndReactionType(post, "LIKE");
        return orderedMap(
                "id", post.getId(),
                "title", safe(post.getTitle()),
                "author", authorName,
                "likes", likeCount,
                "comments", commentCount,
                "createdAt", post.getCreatedAt(),
                "status", post.getStatus() == null ? "UNKNOWN" : post.getStatus().name(),
                "category", safe(post.getCategory())
        );
    }

    private Map<String, Object> toReportSummaryMap(Report report) {
        return orderedMap(
                "id", report.getId(),
                "targetType", normalizeReportTargetType(report.getTargetType()),
                "reason", safe(report.getReason()),
                "reporterCount", reportCount(report.getTargetType(), report.getTargetId()),
                "status", normalizeAdminReportStatus(report.getStatus()),
                "lastReportedAt", report.getCreatedAt(),
                "targetId", report.getTargetId()
        );
    }

    private Map<String, Object> toDispatchMap(AdminNotificationDispatch dispatch) {
        Map<String, Object> metadata = readJsonMap(dispatch.getMetadata());
        return orderedMap(
                "id", dispatch.getId(),
                "title", dispatch.getTitle(),
                "body", dispatch.getBody(),
                "target", dispatch.getTargetKind(),
                "sentAt", dispatch.getCreatedAt(),
                "status", dispatch.getStatus(),
                "targetCount", dispatch.getTargetCount(),
                "deliveredCount", dispatch.getDeliveredCount(),
                "failedCount", dispatch.getFailedCount(),
                "deliveredUserCount", metadataNumber(metadata, "deliveredUserCount", dispatch.getDeliveredCount()),
                "skippedCount", metadataNumber(metadata, "skippedCount", 0),
                "failedTokenCount", metadataNumber(metadata, "failedTokenCount", 0)
        );
    }

    private String normalizeReportTargetType(ReportTargetType targetType) {
        return switch (targetType) {
            case PET_NOTICE -> "NOTICE";
            case COMMUNITY_POST -> "COMMUNITY_POST";
            case COMMUNITY_COMMENT -> "COMMUNITY_COMMENT";
            case NOTICE_CHAT_ROOM -> "NOTICE_CHAT_ROOM";
            case USER -> "USER";
        };
    }

    private String normalizeAdminReportStatus(ReportStatus status) {
        return status == ReportStatus.RECEIVED ? "PENDING" : status.name();
    }

    private boolean isReportTargetingUser(Report report, User user) {
        return report.getTargetType() == ReportTargetType.USER && report.getTargetId().equals(user.getId());
    }

    private long reportCount(ReportTargetType targetType, UUID targetId) {
        return reportRepository.countByTargetTypeAndTargetId(targetType, targetId);
    }

    private String priorityKey(Report report) {
        return report.getTargetType().name() + ":" + report.getTargetId();
    }

    private String resolvePriorityTargetLabel(Report report) {
        return switch (report.getTargetType()) {
            case PET_NOTICE -> petNoticeRepository.findById(report.getTargetId()).map(PetNotice::getTitle).orElse("공고");
            case COMMUNITY_POST -> communityPostRepository.findById(report.getTargetId()).map(CommunityPost::getTitle).orElse("커뮤니티 글");
            case COMMUNITY_COMMENT -> "댓글";
            case NOTICE_CHAT_ROOM -> "채팅방";
            case USER -> userRepository.findById(report.getTargetId()).map(User::getNickname).orElse("사용자");
        };
    }

    private User getUser(String userId) {
        return userRepository.findById(parseUuid(userId, "INVALID_USER_ID", "올바르지 않은 사용자 ID 형식입니다."))
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));
    }

    private PetNotice getNotice(String noticeId) {
        return petNoticeRepository.findById(parseUuid(noticeId, "INVALID_NOTICE_ID", "올바르지 않은 공고 ID 형식입니다."))
                .orElseThrow(() -> ApiException.notFound("NOTICE_NOT_FOUND", "공고를 찾을 수 없습니다."));
    }

    private CommunityPost getCommunityPost(String postId) {
        return communityPostRepository.findById(parseUuid(postId, "INVALID_COMMUNITY_POST_ID", "올바르지 않은 커뮤니티 글 ID 형식입니다."))
                .orElseThrow(() -> ApiException.notFound("COMMUNITY_POST_NOT_FOUND", "커뮤니티 글을 찾을 수 없습니다."));
    }

    private UUID parseUuid(String value, String code, String message) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest(code, message);
        }
    }

    private Instant parseInstant(String value) {
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest("INVALID_DATETIME_FORMAT", "ISO-8601 날짜 형식이 필요합니다.");
        }
    }

    private UserStatus parseNullableUserStatus(String statusValue) {
        if (blank(statusValue)) {
            return null;
        }
        return parseAdminUserStatus(statusValue);
    }

    private UserStatus parseAdminUserStatus(String statusValue) {
        return switch (statusValue.trim().toUpperCase(Locale.ROOT)) {
            case "ACTIVE", "NORMAL" -> UserStatus.ACTIVE;
            case "SUSPENDED", "BANNED", "STOPPED" -> UserStatus.BANNED;
            case "WITHDRAWN", "DELETED" -> UserStatus.WITHDRAWN;
            default -> throw ApiException.badRequest("INVALID_USER_STATUS", "지원하지 않는 사용자 상태입니다.");
        };
    }

    private String normalizeUserStatus(UserStatus status) {
        if (status == null) {
            return "ACTIVE";
        }
        return switch (status) {
            case ACTIVE -> "ACTIVE";
            case BANNED -> "SUSPENDED";
            case WITHDRAWN -> "WITHDRAWN";
        };
    }

    private UserRole parseNullableRole(String roleValue) {
        if (blank(roleValue)) {
            return null;
        }
        return parseRole(roleValue);
    }

    private UserRole parseRole(String roleValue) {
        try {
            return UserRole.valueOf(roleValue.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("INVALID_USER_ROLE", "지원하지 않는 사용자 역할입니다.");
        }
    }

    private CommunityPostStatus parseNullableCommunityStatus(String statusValue) {
        if (blank(statusValue)) {
            return null;
        }
        try {
            return CommunityPostStatus.valueOf(statusValue.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("INVALID_COMMUNITY_STATUS", "지원하지 않는 커뮤니티 상태입니다.");
        }
    }

    private Object readJsonValue(String value) {
        if (blank(value)) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException e) {
            return value;
        }
    }

    private Map<String, Object> readJsonMap(String value) {
        if (blank(value)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, STRING_OBJECT_MAP_TYPE);
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    private int metadataNumber(Map<String, Object> metadata, String key, int defaultValue) {
        Object value = metadata.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private Map<String, Object> mapOfNullable(Object value) {
        if (value == null) {
            return Map.of();
        }
        return objectMapper.convertValue(value, STRING_OBJECT_MAP_TYPE);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }

    private String settingValue(String key, String defaultValue) {
        return adminSettingRepository.findBySettingKey(key)
                .map(AdminSetting::getSettingValue)
                .orElse(defaultValue);
    }

    private boolean settingBoolean(String key, boolean defaultValue) {
        return adminSettingRepository.findBySettingKey(key)
                .map(AdminSetting::getSettingValue)
                .map(Boolean::parseBoolean)
                .orElse(defaultValue);
    }

    private String nullableString(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? null : text;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private Map<String, Object> orderedMap(Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("orderedMap requires key/value pairs.");
        }
        Map<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            map.put(String.valueOf(keyValues[index]), keyValues[index + 1]);
        }
        return map;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
