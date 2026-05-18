package com.example.pogun.service.missingpet;

import com.example.pogun.dto.ai.AiAnalysisResultCallbackRequest;
import com.example.pogun.dto.ai.AiAnalysisResultCallbackResponse;
import com.example.pogun.dto.ai.SimilarNoticeListResponse;
import com.example.pogun.dto.missingpet.MissingPetDetailResponse;
import com.example.pogun.dto.missingpet.MissingPetListFiltersResponse;
import com.example.pogun.dto.missingpet.MissingPetListResponse;
import com.example.pogun.dto.missingpet.MissingPetSummaryResponse;
import com.example.pogun.dto.missingpet.MissingPetViewResponse;
import com.example.pogun.dto.common.ApiResponse.ApiException;
import com.example.pogun.entity.bookmark.NoticeBookmark;
import com.example.pogun.entity.missingpet.PetNotice;
import com.example.pogun.entity.missingpet.PetNoticeImage;
import com.example.pogun.entity.user.User;
import com.example.pogun.service.ai.AiService;
import com.example.pogun.service.cache.AiSourceCacheService;
import com.example.pogun.service.notification.NotificationService;
import com.example.pogun.service.storage.S3ImageStorageService;
import com.example.pogun.entity.notification.enums.NotificationTargetType;
import com.example.pogun.entity.notification.enums.NotificationType;
import com.example.pogun.entity.notification.enums.NotificationPriority;
import com.example.pogun.entity.missingpet.enums.PetGender;
import com.example.pogun.entity.missingpet.enums.PetNoticeStatus;
import com.example.pogun.repository.bookmark.NoticeBookmarkRepository;
import com.example.pogun.repository.missingpet.PetNoticeRepository;
import com.example.pogun.repository.user.UserRepository;
import com.example.pogun.service.noticechat.NoticeChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.time.Duration;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
/**
 * 도메인 비즈니스 로직을 담당하는 MissingPetService이다.
 */

@Service
@RequiredArgsConstructor
public class MissingPetService {
    private final PetNoticeRepository petNoticeRepository;
    private final UserRepository userRepository;
    private final NoticeBookmarkRepository noticeBookmarkRepository;
    private final NotificationService notificationService;
    private final S3ImageStorageService s3ImageStorageService;
    private final SimpMessagingTemplate simpMessagingTemplate;
    private final AiService aiService;
    private final NoticeChatService noticeChatService;
    private final AiSourceCacheService aiSourceCacheService;

    private static final String AI_CACHE_NAMESPACE = "missing-pets";

    @Value("${app.ai-source-cache.ttl-seconds:60}")
    private long aiSourceCacheTtlSeconds;

    @Transactional(readOnly = true)
    public MissingPetListResponse getMissingPetList(String region, String breed, String status, String from, String to, String sort, int page, int size) {
        String requestedRegion = normalizeTextFilter(region);
        String requestedBreed = normalizeTextFilter(breed);
        PetNoticeStatus requestedStatus = parseStatus(status);
        Instant requestedFrom = parseInstant(from);
        Instant requestedTo = parseInstant(to);
        int normalizedPage = normalizePage(page);
        int normalizedSize = normalizeSize(size);

        List<PetNotice> filteredNotices = petNoticeRepository.findNotices(
                requestedStatus,
                requestedFrom,
                requestedTo
        ).stream()
                .filter(notice -> containsIgnoreCase(notice.getMissingRegion(), requestedRegion))
                .filter(notice -> containsIgnoreCase(notice.getBreed(), requestedBreed))
                .toList();
        List<PetNotice> sortedNotices = sortNotices(filteredNotices, sort);

        int fromIndex = Math.min(normalizedPage * normalizedSize, sortedNotices.size());
        int toIndex = Math.min(fromIndex + normalizedSize, sortedNotices.size());
        List<PetNotice> pageItems = sortedNotices.subList(fromIndex, toIndex);

        return new MissingPetListResponse(
                new MissingPetListFiltersResponse(region, breed, status, from, to, sort, normalizedPage, normalizedSize),
                sortedNotices.size(),
                (int) Math.ceil((double) sortedNotices.size() / normalizedSize),
                pageItems.stream().map(this::toNoticeSummary).toList()
        );
    }

    // 공고 생성 시 작성자 연관과 이미지 정규화를 한 번에 처리하고, 등록 확인 알림도 같은 흐름에서 남긴다.
    @Transactional
    public MissingPetDetailResponse createMissingPet(Map<String, Object> request) {
        return createMissingPet(request, List.of());
    }

    @Transactional
    public MissingPetDetailResponse createMissingPet(Map<String, Object> request, List<MultipartFile> imageFiles) {
        User author = getCurrentUser();

        PetNotice notice = PetNotice.builder()
                .author(author)
                .title(requiredString(request, "title"))
                .animalType(requiredString(request, "animalType"))
                .breed(optionalString(request, "breed"))
                .gender(parseGender(optionalString(request, "gender")))
                .age(parseInteger(request.get("age")))
                .color(optionalString(request, "color"))
                .description(optionalString(request, "description"))
                .missingDate(parseInstantRequired(request.get("missingDate")))
                .missingRegion(requiredString(request, "missingRegion"))
                .missingAddress(optionalString(request, "missingAddress"))
                .rewardAmount(parseInteger(request.get("rewardAmount")))
                .contactPhone(optionalString(request, "contactPhone"))
                .status(parseStatusOrDefault(optionalString(request, "status"), PetNoticeStatus.OPEN))
                .hidden(false)
                .build();

        attachImages(author.getId(), notice, imageFiles, true);

        PetNotice saved = petNoticeRepository.saveAndFlush(notice);
        notificationService.createAndSendNotification(
                author,
                NotificationType.NEW_NOTICE,
                NotificationTargetType.PET_NOTICE,
                saved.getId(),
                "실종 공고가 등록되었습니다.",
                saved.getTitle() + " 공고 등록이 완료되었습니다.",
                Map.of("status", saved.getStatus().name())
        );
        MissingPetDetailResponse response = toNoticeDetail(saved);
        afterCommitOrNow(() -> {
            aiSourceCacheService.bumpVersion(AI_CACHE_NAMESPACE);
            simpMessagingTemplate.convertAndSend("/topic/missing-pets", response);
        });
        return response;
    }

    @Transactional(readOnly = true)
    public MissingPetDetailResponse getMissingPetDetail(String missingPetId) {
        PetNotice notice = getVisibleNotice(missingPetId);
        return toNoticeDetail(notice);
    }

    @Transactional
    public MissingPetDetailResponse updateMissingPet(String missingPetId, Map<String, Object> request) {
        return updateMissingPet(missingPetId, request, List.of());
    }

    @Transactional
    public MissingPetDetailResponse updateMissingPet(String missingPetId, Map<String, Object> request, List<MultipartFile> imageFiles) {
        PetNotice notice = getOwnedNotice(missingPetId);

        if (request.containsKey("title")) notice.setTitle(requiredString(request, "title"));
        if (request.containsKey("animalType")) notice.setAnimalType(requiredString(request, "animalType"));
        if (request.containsKey("breed")) notice.setBreed(optionalString(request, "breed"));
        if (request.containsKey("gender")) notice.setGender(parseGender(optionalString(request, "gender")));
        if (request.containsKey("age")) notice.setAge(parseInteger(request.get("age")));
        if (request.containsKey("color")) notice.setColor(optionalString(request, "color"));
        if (request.containsKey("description")) notice.setDescription(optionalString(request, "description"));
        if (request.containsKey("missingDate")) notice.setMissingDate(parseInstantRequired(request.get("missingDate")));
        if (request.containsKey("missingRegion")) notice.setMissingRegion(requiredString(request, "missingRegion"));
        if (request.containsKey("missingAddress")) notice.setMissingAddress(optionalString(request, "missingAddress"));
        if (request.containsKey("rewardAmount")) notice.setRewardAmount(parseInteger(request.get("rewardAmount")));
        if (request.containsKey("contactPhone")) notice.setContactPhone(optionalString(request, "contactPhone"));
        if (request.containsKey("status")) notice.setStatus(parseStatusOrDefault(optionalString(request, "status"), notice.getStatus()));
        attachImages(notice.getAuthor().getId(), notice, imageFiles, imageFiles != null && !imageFiles.isEmpty());

        PetNotice saved = petNoticeRepository.saveAndFlush(notice);
        MissingPetDetailResponse response = toNoticeDetail(saved);
        UUID savedNoticeId = saved.getId();
        afterCommitOrNow(() -> {
            aiSourceCacheService.bumpVersion(AI_CACHE_NAMESPACE);
            simpMessagingTemplate.convertAndSend("/topic/missing-pets", response);
            noticeChatService.syncNoticeRooms(savedNoticeId);
        });
        return response;
    }

    // 상태 변경은 작성자 권한 검증뿐 아니라, 북마크한 사용자들에게 팬아웃 알림을 보내는 지점이기도 하다.
    @Transactional
    public MissingPetDetailResponse changeMissingPetStatus(String missingPetId, String status) {
        PetNotice notice = getOwnedNotice(missingPetId);
        notice.setStatus(parseStatusOrDefault(status, notice.getStatus()));
        PetNotice saved = petNoticeRepository.saveAndFlush(notice);

        for (NoticeBookmark bookmark : noticeBookmarkRepository.findByNotice(saved)) {
            User bookmarkedUser = bookmark.getUser();
            notificationService.createAndSendNotification(
                    bookmarkedUser,
                    NotificationType.NOTICE_STATUS_CHANGED,
                    NotificationTargetType.PET_NOTICE,
                    saved.getId(),
                    "즐겨찾기한 공고 상태가 변경되었습니다.",
                    saved.getTitle() + " 공고 상태가 " + getStatusLabel(effectiveStatus(saved)) + "로 변경되었습니다.",
                    Map.of("status", saved.getStatus().name())
            );
        }

        MissingPetDetailResponse response = toNoticeDetail(saved);
        UUID savedNoticeId = saved.getId();
        afterCommitOrNow(() -> {
            aiSourceCacheService.bumpVersion(AI_CACHE_NAMESPACE);
            simpMessagingTemplate.convertAndSend("/topic/missing-pets", response);
            noticeChatService.syncNoticeRooms(savedNoticeId);
        });
        return response;
    }

    @Transactional
    public void deleteMissingPet(String missingPetId) {
        PetNotice notice = getOwnedNotice(missingPetId);
        UUID noticeId = notice.getId();
        noticeChatService.deleteRoomsByNotice(notice);
        noticeBookmarkRepository.deleteByNotice(notice);
        petNoticeRepository.delete(notice);
        afterCommitOrNow(() -> {
            aiSourceCacheService.bumpVersion(AI_CACHE_NAMESPACE);
            simpMessagingTemplate.convertAndSend("/topic/missing-pets", (Object) Map.of(
                "type", "NOTICE_DELETED",
                "noticeId", noticeId.toString()
            ));
        });
    }

    @Transactional
    public MissingPetViewResponse increaseMissingPetView(String missingPetId) {
        PetNotice notice = getVisibleNotice(missingPetId);
        notice.setViewCount(notice.getViewCount() + 1);
        PetNotice saved = petNoticeRepository.save(notice);
        return new MissingPetViewResponse(saved.getId(), saved.getViewCount());
    }

    @Transactional(readOnly = true)
    public MissingPetListResponse getAiSourceList(String apiKey, String region, String breed, String status, String from, String to, String sort, int page, int size) {
        aiService.verifyAiApiKey(apiKey);
        String cacheKey = String.join(":",
            "list",
            "v" + aiSourceCacheService.currentVersion(AI_CACHE_NAMESPACE),
            normalizeCacheValue(region),
            normalizeCacheValue(breed),
            normalizeCacheValue(status),
            normalizeCacheValue(from),
            normalizeCacheValue(to),
            normalizeCacheValue(sort),
            String.valueOf(page),
            String.valueOf(size)
        );
        return aiSourceCacheService.getOrLoad(
            cacheKey,
            Duration.ofSeconds(aiSourceCacheTtlSeconds),
            MissingPetListResponse.class,
            () -> getMissingPetList(region, breed, status, from, to, sort, page, size)
        );
    }

    @Transactional(readOnly = true)
    public MissingPetDetailResponse getAiSourceDetail(String apiKey, String missingPetId) {
        aiService.verifyAiApiKey(apiKey);
        String cacheKey = String.join(":",
            "detail",
            "v" + aiSourceCacheService.currentVersion(AI_CACHE_NAMESPACE),
            normalizeCacheValue(missingPetId)
        );
        return aiSourceCacheService.getOrLoad(
            cacheKey,
            Duration.ofSeconds(aiSourceCacheTtlSeconds),
            MissingPetDetailResponse.class,
            () -> getMissingPetDetail(missingPetId)
        );
    }

    @Transactional(readOnly = true)
    public SimilarNoticeListResponse getSimilarNotices(String missingPetId) {
        PetNotice notice = getVisibleNotice(missingPetId);
        return aiService.getLatestSimilarNotices("MISSING_PET", notice.getId().toString());
    }

    @Transactional
    public AiAnalysisResultCallbackResponse receiveAnalysisResult(String missingPetId, String apiKey, AiAnalysisResultCallbackRequest request) {
        AiAnalysisResultCallbackResponse response = aiService.saveMissingPetAnalysisResult(missingPetId, apiKey, request);
        PetNotice notice = getVisibleNotice(missingPetId);
        SimilarNoticeListResponse similar = aiService.getLatestSimilarNotices(response.targetType(), response.targetId());
        List<String> filteredSimilarIds = similar.items().stream().map(item -> item.noticeId()).toList();
        sendSimilarNoticeFoundNotification(response.analysisId(), notice, filteredSimilarIds);
        return response;
    }

    private void sendSimilarNoticeFoundNotification(String analysisId, PetNotice notice, List<String> similarNoticeIds) {
        if (notice == null || notice.getAuthor() == null || similarNoticeIds == null || similarNoticeIds.isEmpty()) {
            return;
        }
        long distinctCount = similarNoticeIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .map(String::trim)
                .distinct()
                .count();
        if (distinctCount <= 0) {
            return;
        }
        String dedupeKey = "ai-similar:missing:" + analysisId + ":" + notice.getAuthor().getId();
        notificationService.createAndSendNotification(
                notice.getAuthor(),
                null,
                NotificationType.AI_SIMILAR_NOTICE_FOUND,
                NotificationTargetType.PET_NOTICE,
                notice.getId(),
                "유사 공고가 발견되었습니다.",
                "AI가 " + distinctCount + "건의 유사 공고를 찾았습니다. 공고를 확인해 주세요.",
                NotificationPriority.NORMAL,
                dedupeKey,
                Map.of("similarCount", String.valueOf(distinctCount))
        );
    }

    private User getCurrentUser() {
        String firebaseUid = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return userRepository.findByFirebaseUid(firebaseUid)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));
    }

    private PetNotice getNotice(String missingPetId) {
        return petNoticeRepository.findById(parseUuid(missingPetId))
                .orElseThrow(() -> ApiException.notFound("NOTICE_NOT_FOUND", "실종 공고를 찾을 수 없습니다."));
    }

    private PetNotice getVisibleNotice(String missingPetId) {
        PetNotice notice = getNotice(missingPetId);
        if (Boolean.TRUE.equals(notice.getHidden())) {
            throw ApiException.notFound("NOTICE_NOT_FOUND", "실종 공고를 찾을 수 없습니다.");
        }
        return notice;
    }

    private PetNotice getOwnedNotice(String missingPetId) {
        PetNotice notice = getNotice(missingPetId);
        User currentUser = getCurrentUser();
        if (!notice.getAuthor().getId().equals(currentUser.getId())) {
            throw ApiException.forbidden("NOTICE_FORBIDDEN", "해당 실종 공고에 대한 권한이 없습니다.");
        }
        return notice;
    }

    private MissingPetSummaryResponse toNoticeSummary(PetNotice notice) {
        PetNoticeStatus status = effectiveStatus(notice);
        User author = notice.getAuthor();
        return new MissingPetSummaryResponse(
                notice.getId(),
                notice.getTitle(),
                notice.getAnimalType(),
                notice.getBreed(),
                notice.getMissingDate(),
                notice.getMissingRegion(),
                status.name(),
                getStatusLabel(status),
                status != PetNoticeStatus.OPEN,
                notice.getViewCount() == null ? 0L : notice.getViewCount(),
                noticeBookmarkRepository.countByNotice(notice),
                isUrgent(notice),
                author == null ? null : author.getId(),
                displayAuthorName(author),
                notice.getImages() == null
                        ? List.of()
                        : notice.getImages().stream().map(PetNoticeImage::getImageUrl).toList()
        );
    }

    private MissingPetDetailResponse toNoticeDetail(PetNotice notice) {
        PetNoticeStatus status = effectiveStatus(notice);
        User author = notice.getAuthor();
        return new MissingPetDetailResponse(
                notice.getId(),
                notice.getTitle(),
                notice.getAnimalType(),
                notice.getBreed(),
                notice.getGender() == null ? PetGender.UNKNOWN.name() : notice.getGender().name(),
                notice.getAge(),
                notice.getColor(),
                notice.getDescription(),
                notice.getMissingDate(),
                notice.getMissingRegion(),
                notice.getMissingAddress(),
                notice.getRewardAmount(),
                notice.getContactPhone(),
                status.name(),
                getStatusLabel(status),
                status != PetNoticeStatus.OPEN,
                notice.getViewCount() == null ? 0L : notice.getViewCount(),
                noticeBookmarkRepository.countByNotice(notice),
                isUrgent(notice),
                notice.getHidden(),
                author == null ? null : author.getId(),
                displayAuthorName(author),
                notice.getCreatedAt(),
                notice.getUpdatedAt(),
                notice.getImages() == null
                        ? List.of()
                        : notice.getImages().stream().map(PetNoticeImage::getImageUrl).toList()
        );
    }

    private String displayAuthorName(User author) {
        if (author == null) {
            return "알 수 없는 사용자";
        }
        return author.getNickname();
    }

    private void attachImages(UUID ownerId, PetNotice notice, List<MultipartFile> imageFiles, boolean shouldReplaceImages) {
        if (!shouldReplaceImages) {
            return;
        }

        notice.getImages().clear();
        List<String> resolvedImageUrls = new ArrayList<>();
        if (imageFiles != null && !imageFiles.isEmpty()) {
            resolvedImageUrls.addAll(s3ImageStorageService.storeImages("missing-pets", "notices", ownerId, imageFiles));
        }

        for (int i = 0; i < resolvedImageUrls.size(); i++) {
            notice.getImages().add(PetNoticeImage.builder()
                    .notice(notice)
                    .imageUrl(resolvedImageUrls.get(i))
                    .sortOrder(i)
                    .build());
        }
    }

    // urgent 정렬은 아직 기획 고정값이 아니라 OPEN/실종시점/사례금 기반 점수로 계산한다.
    private List<PetNotice> sortNotices(List<PetNotice> notices, String sort) {
        Comparator<PetNotice> comparator;
        String normalizedSort = sort == null || sort.isBlank() ? "latest" : sort.trim().toLowerCase();

        comparator = switch (normalizedSort) {
            case "view", "views", "popular", "viewcount" ->
                    Comparator.comparing(PetNotice::getViewCount, Comparator.nullsLast(Long::compareTo)).reversed()
                            .thenComparing(PetNotice::getCreatedAt, Comparator.nullsLast(Instant::compareTo)).reversed();
            case "urgent", "emergency" ->
                    Comparator.comparing(this::urgencyScore, Comparator.reverseOrder())
                            .thenComparing(PetNotice::getMissingDate, Comparator.nullsLast(Instant::compareTo)).reversed()
                            .thenComparing(PetNotice::getCreatedAt, Comparator.nullsLast(Instant::compareTo)).reversed();
            default ->
                    Comparator.comparing(PetNotice::getCreatedAt, Comparator.nullsLast(Instant::compareTo)).reversed();
        };

        return notices.stream().sorted(comparator).toList();
    }

    private PetNoticeStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return PetNoticeStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("INVALID_NOTICE_STATUS", "올바르지 않은 status 값입니다.");
        }
    }

    private PetNoticeStatus parseStatusOrDefault(String status, PetNoticeStatus defaultValue) {
        return status == null || status.isBlank() ? defaultValue : parseStatus(status);
    }

    private PetGender parseGender(String gender) {
        if (gender == null || gender.isBlank()) {
            return PetGender.UNKNOWN;
        }
        try {
            return PetGender.valueOf(gender.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("INVALID_PET_GENDER", "올바르지 않은 gender 값입니다.");
        }
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest("INVALID_DATETIME_FORMAT", "날짜 형식이 올바르지 않습니다. ISO-8601 형식을 사용해주세요.");
        }
    }

    private Instant parseInstantRequired(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            throw ApiException.badRequest("MISSING_REQUIRED_FIELD", "missingDate는 필수입니다.", Map.of("field", "missingDate"));
        }
        return parseInstant(String.valueOf(value));
    }

    private Integer parseInteger(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("INVALID_NUMBER", "숫자 형식이 올바르지 않습니다.");
        }
    }

    private String requiredString(Map<String, Object> request, String key) {
        String value = optionalString(request, key);
        if (value == null || value.isBlank()) {
            throw ApiException.badRequest("MISSING_REQUIRED_FIELD", key + "는 필수입니다.", Map.of("field", key));
        }
        return value;
    }

    private String optionalString(Map<String, Object> request, String key) {
        return optionalString(request.get(key));
    }

    private String optionalString(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizeTextFilter(String value) {
        String normalized = blankToNull(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private boolean containsIgnoreCase(String value, String keyword) {
        if (keyword == null) {
            return true;
        }
        if (value == null || value.isBlank()) {
            return false;
        }
        return value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private int normalizePage(int page) {
        return Math.max(page, 0);
    }

    private int normalizeSize(int size) {
        if (size <= 0) {
            return 20;
        }
        return Math.min(size, 100);
    }

    private String normalizeCacheValue(String value) {
        if (value == null) {
            return "_";
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? "_" : normalized;
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("INVALID_NOTICE_ID", "올바르지 않은 공고 ID 형식입니다.");
        }
    }

    private boolean isUrgent(PetNotice notice) {
        return urgencyScore(notice) >= 2;
    }

    private int urgencyScore(PetNotice notice) {
        int score = 0;
        if (effectiveStatus(notice) == PetNoticeStatus.OPEN) {
            score += 2;
        }
        if (notice.getMissingDate() != null && notice.getMissingDate().isAfter(Instant.now().minus(3, ChronoUnit.DAYS))) {
            score += 2;
        }
        if (notice.getRewardAmount() != null && notice.getRewardAmount() > 0) {
            score += 1;
        }
        return score;
    }

    private PetNoticeStatus effectiveStatus(PetNotice notice) {
        if (notice == null || notice.getStatus() == null) {
            return PetNoticeStatus.OPEN;
        }
        return notice.getStatus();
    }

    private String getStatusLabel(PetNoticeStatus status) {
        return switch (status) {
            case OPEN -> "진행중";
            case RESOLVED -> "해결됨";
            case CLOSED -> "종료됨";
        };
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
}

