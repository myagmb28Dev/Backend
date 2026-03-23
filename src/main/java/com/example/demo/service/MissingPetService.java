package com.example.demo.service;

import com.example.demo.entity.PetNotice;
import com.example.demo.entity.PetNoticeImage;
import com.example.demo.entity.User;
import com.example.demo.entity.NoticeBookmark;
import com.example.demo.entity.enums.NotificationTargetType;
import com.example.demo.entity.enums.NotificationType;
import com.example.demo.entity.enums.PetGender;
import com.example.demo.entity.enums.PetNoticeStatus;
import com.example.demo.repository.NoticeBookmarkRepository;
import com.example.demo.repository.PetNoticeRepository;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MissingPetService {
    private final PetNoticeRepository petNoticeRepository;
    private final UserRepository userRepository;
    private final NoticeBookmarkRepository noticeBookmarkRepository;
    private final NotificationService notificationService;

    public Map<String, Object> getMissingPetList(String region, String breed, String status, String from, String to, String sort, int page, int size) {
        List<PetNotice> filteredNotices = petNoticeRepository.findNotices(
                blankToNull(region),
                blankToNull(breed),
                parseStatus(status),
                parseInstant(from),
                parseInstant(to)
        );

        List<PetNotice> sortedNotices = sortNotices(filteredNotices, sort);
        int fromIndex = Math.min(page * size, sortedNotices.size());
        int toIndex = Math.min(fromIndex + size, sortedNotices.size());
        List<PetNotice> pageItems = sortedNotices.subList(fromIndex, toIndex);

        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("region", region);
        filters.put("breed", breed);
        filters.put("status", status);
        filters.put("from", from);
        filters.put("to", to);
        filters.put("sort", sort);
        filters.put("page", page);
        filters.put("size", size);

        return Map.of(
                "filters", filters,
                "totalElements", sortedNotices.size(),
                "totalPages", size == 0 ? 0 : (int) Math.ceil((double) sortedNotices.size() / size),
                "items", pageItems.stream().map(this::toNoticeSummary).toList()
        );
    }

    @Transactional
    public Map<String, Object> createMissingPet(Map<String, Object> request) {
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
                .build();

        appendImages(notice, request.get("imageUrls"));

        PetNotice saved = petNoticeRepository.save(notice);
        notificationService.createAndSendNotification(
                author,
                NotificationType.NEW_NOTICE,
                NotificationTargetType.PET_NOTICE,
                saved.getId(),
                "실종 공고가 등록되었습니다.",
                saved.getTitle() + " 공고 등록이 완료되었습니다.",
                Map.of("status", saved.getStatus().name())
        );
        return toNoticeDetail(saved);
    }

    public Map<String, Object> getMissingPetDetail(String missingPetId) {
        PetNotice notice = getNotice(missingPetId);
        return toNoticeDetail(notice);
    }

    @Transactional
    public Map<String, Object> updateMissingPet(String missingPetId, Map<String, Object> request) {
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
        if (request.containsKey("imageUrls")) {
            notice.getImages().clear();
            appendImages(notice, request.get("imageUrls"));
        }

        return toNoticeDetail(petNoticeRepository.save(notice));
    }

    @Transactional
    public Map<String, Object> changeMissingPetStatus(String missingPetId, String status) {
        PetNotice notice = getOwnedNotice(missingPetId);
        notice.setStatus(parseStatusOrDefault(status, notice.getStatus()));
        PetNotice saved = petNoticeRepository.save(notice);

        for (NoticeBookmark bookmark : noticeBookmarkRepository.findByNotice(saved)) {
            User bookmarkedUser = bookmark.getUser();
            notificationService.createAndSendNotification(
                    bookmarkedUser,
                    NotificationType.NOTICE_STATUS_CHANGED,
                    NotificationTargetType.PET_NOTICE,
                    saved.getId(),
                    "즐겨찾기한 공고 상태가 변경되었습니다.",
                    saved.getTitle() + " 공고 상태가 " + getStatusLabel(saved) + "로 변경되었습니다.",
                    Map.of("status", saved.getStatus().name())
            );
        }

        return toNoticeDetail(saved);
    }

    @Transactional
    public Map<String, Object> increaseMissingPetView(String missingPetId) {
        PetNotice notice = getNotice(missingPetId);
        notice.setViewCount(notice.getViewCount() + 1);
        PetNotice saved = petNoticeRepository.save(notice);
        return Map.of("id", saved.getId(), "viewCount", saved.getViewCount());
    }

    private User getCurrentUser() {
        String firebaseUid = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return userRepository.findByFirebaseUid(firebaseUid)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));
    }

    private PetNotice getNotice(String missingPetId) {
        return petNoticeRepository.findById(parseUuid(missingPetId))
                .orElseThrow(() -> new RuntimeException("실종 공고를 찾을 수 없습니다."));
    }

    private PetNotice getOwnedNotice(String missingPetId) {
        PetNotice notice = getNotice(missingPetId);
        User currentUser = getCurrentUser();
        if (!notice.getAuthor().getId().equals(currentUser.getId())) {
            throw new RuntimeException("해당 실종 공고에 대한 권한이 없습니다.");
        }
        return notice;
    }

    private Map<String, Object> toNoticeSummary(PetNotice notice) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", notice.getId());
        item.put("title", notice.getTitle());
        item.put("animalType", notice.getAnimalType());
        item.put("breed", notice.getBreed());
        item.put("missingDate", notice.getMissingDate());
        item.put("missingRegion", notice.getMissingRegion());
        item.put("status", notice.getStatus().name());
        item.put("statusLabel", getStatusLabel(notice));
        item.put("statusChanged", notice.getStatus() != PetNoticeStatus.OPEN);
        item.put("viewCount", notice.getViewCount());
        item.put("bookmarkCount", noticeBookmarkRepository.countByNotice(notice));
        item.put("isUrgent", isUrgent(notice));
        item.put("authorNickname", notice.getAuthor().getNickname());
        item.put("imageUrls", notice.getImages().stream().map(PetNoticeImage::getImageUrl).toList());
        return item;
    }

    private Map<String, Object> toNoticeDetail(PetNotice notice) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", notice.getId());
        data.put("title", notice.getTitle());
        data.put("animalType", notice.getAnimalType());
        data.put("breed", notice.getBreed());
        data.put("gender", notice.getGender().name());
        data.put("age", notice.getAge());
        data.put("color", notice.getColor());
        data.put("description", notice.getDescription());
        data.put("missingDate", notice.getMissingDate());
        data.put("missingRegion", notice.getMissingRegion());
        data.put("missingAddress", notice.getMissingAddress());
        data.put("rewardAmount", notice.getRewardAmount());
        data.put("contactPhone", notice.getContactPhone());
        data.put("status", notice.getStatus().name());
        data.put("statusLabel", getStatusLabel(notice));
        data.put("statusChanged", notice.getStatus() != PetNoticeStatus.OPEN);
        data.put("viewCount", notice.getViewCount());
        data.put("bookmarkCount", noticeBookmarkRepository.countByNotice(notice));
        data.put("isUrgent", isUrgent(notice));
        data.put("authorId", notice.getAuthor().getId());
        data.put("authorNickname", notice.getAuthor().getNickname());
        data.put("createdAt", notice.getCreatedAt());
        data.put("updatedAt", notice.getUpdatedAt());
        data.put("imageUrls", notice.getImages().stream().map(PetNoticeImage::getImageUrl).toList());
        return data;
    }

    private void appendImages(PetNotice notice, Object imageUrlsValue) {
        if (!(imageUrlsValue instanceof List<?> imageUrls)) {
            return;
        }

        for (int i = 0; i < imageUrls.size(); i++) {
            String imageUrl = String.valueOf(imageUrls.get(i)).trim();
            if (!imageUrl.isEmpty()) {
                notice.getImages().add(PetNoticeImage.builder()
                        .notice(notice)
                        .imageUrl(imageUrl)
                        .sortOrder(i)
                        .build());
            }
        }
    }

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
        return PetNoticeStatus.valueOf(status.trim().toUpperCase());
    }

    private PetNoticeStatus parseStatusOrDefault(String status, PetNoticeStatus defaultValue) {
        return status == null || status.isBlank() ? defaultValue : parseStatus(status);
    }

    private PetGender parseGender(String gender) {
        if (gender == null || gender.isBlank()) {
            return PetGender.UNKNOWN;
        }
        return PetGender.valueOf(gender.trim().toUpperCase());
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new RuntimeException("날짜 형식이 올바르지 않습니다. ISO-8601 형식을 사용해주세요.");
        }
    }

    private Instant parseInstantRequired(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            throw new RuntimeException("missingDate는 필수입니다.");
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
        return Integer.parseInt(String.valueOf(value).trim());
    }

    private String requiredString(Map<String, Object> request, String key) {
        String value = optionalString(request, key);
        if (value == null || value.isBlank()) {
            throw new RuntimeException(key + "는 필수입니다.");
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

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("올바르지 않은 공고 ID 형식입니다.");
        }
    }

    private boolean isUrgent(PetNotice notice) {
        return urgencyScore(notice) >= 2;
    }

    private int urgencyScore(PetNotice notice) {
        int score = 0;
        if (notice.getStatus() == PetNoticeStatus.OPEN) {
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

    private String getStatusLabel(PetNotice notice) {
        return switch (notice.getStatus()) {
            case OPEN -> "진행중";
            case RESOLVED -> "해결됨";
            case CLOSED -> "종료됨";
        };
    }
}
