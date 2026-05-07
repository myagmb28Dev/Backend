package com.example.pogun.service.shelterpet;

import com.example.pogun.dto.ai.AiAnalysisResultCallbackRequest;
import com.example.pogun.dto.ai.AiAnalysisResultCallbackResponse;
import com.example.pogun.dto.shelterpet.ShelterPetDetailResponse;
import com.example.pogun.dto.shelterpet.ShelterPetListFiltersResponse;
import com.example.pogun.dto.shelterpet.ShelterPetListResponse;
import com.example.pogun.dto.shelterpet.ShelterPetSummaryResponse;
import com.example.pogun.entity.shelterpet.ShelterPet;
import com.example.pogun.repository.shelterpet.ShelterPetRepository;
import com.example.pogun.service.ai.AiService;
import com.example.pogun.service.cache.AiSourceCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.time.Duration;
import java.util.Locale;

/**
 * 도메인 비즈니스 로직을 담당하는 ShelterPetService이다.
 */
@Service
@RequiredArgsConstructor
public class ShelterPetService {

    private final ShelterPublicApiClient shelterPublicApiClient;
    private final ShelterPetRepository shelterPetRepository;
    private final AiService aiService;
    private final AiSourceCacheService aiSourceCacheService;

    private static final String AI_CACHE_NAMESPACE = "shelter-pets";

    @Value("${app.ai-source-cache.ttl-seconds:60}")
    private long aiSourceCacheTtlSeconds;

    public ShelterPetListResponse getShelterPetList(String region, String breed, String status, String sort, int page, int size) {
        int normalizedPage = normalizePage(page);
        int normalizedSize = normalizeSize(size);
        String requestedRegion = normalizeTextFilter(region);
        String requestedBreed = normalizeTextFilter(breed);
        boolean regionCodeFilter = isCodeFilter(requestedRegion);
        boolean breedCodeFilter = isCodeFilter(requestedBreed);
        ShelterPublicApiClient.ShelterPublicApiPage result = shelterPublicApiClient.fetchShelterPets(
                regionCodeFilter ? requestedRegion : null,
                breedCodeFilter ? requestedBreed : null,
                status,
                sort,
                0,
                1000
        );
        List<ShelterPublicApiClient.ShelterPublicApiAnimal> filteredItems = result.items().stream()
                .filter(item -> matchesStatus(item, status))
                .filter(item -> matchesRegion(item, requestedRegion, regionCodeFilter))
                .filter(item -> matchesBreed(item, requestedBreed, breedCodeFilter))
                .toList();

        int fromIndex = Math.min(normalizedPage * normalizedSize, filteredItems.size());
        int toIndex = Math.min(fromIndex + normalizedSize, filteredItems.size());

        List<ShelterPetSummaryResponse> items = filteredItems.subList(fromIndex, toIndex).stream()
                .map(item -> {
                    ShelterPet local = shelterPetRepository.findById(item.desertionNo()).orElse(null);
                    return new ShelterPetSummaryResponse(
                            item.desertionNo(),
                            item.noticeNo(),
                            resolveTitle(local, item),
                            resolveRegionLabel(local, item),
                            resolveBreedLabel(local, item),
                            resolveStatusLabel(local, item),
                            item.happenPlace(),
                            item.careName(),
                            item.noticeStartDate(),
                            item.noticeEndDate(),
                            resolveImages(local, item)
                    );
                })
                .toList();

        return new ShelterPetListResponse(
                "KOREA_ANIMAL_PROTECTION_API",
                new ShelterPetListFiltersResponse(region, breed, status, sort, normalizedPage, normalizedSize),
                filteredItems.size(),
                items
        );
    }

    public ShelterPetDetailResponse getShelterPetDetail(String id) {
        ShelterPublicApiClient.ShelterPublicApiAnimal external = shelterPublicApiClient.fetchShelterPet(id);
        ShelterPet local = shelterPetRepository.findById(id).orElse(null);
        return new ShelterPetDetailResponse(
                id,
                external.noticeNo(),
                resolveTitle(local, external),
                firstNonBlank(local == null ? null : local.getStatus(), external.processState(), "UNKNOWN"),
                firstNonBlank(local == null ? null : local.getBreed(), external.kindFullName(), external.kindName(), "미상"),
                resolveDescription(local, external),
                local == null ? null : local.getRewardAmount(),
                firstNonBlank(local == null ? null : local.getContactPhone(), external.careTel()),
                external.happenPlace(),
                external.happenDate(),
                external.careName(),
                external.careAddress(),
                external.organizationName(),
                external.noticeStartDate(),
                external.noticeEndDate(),
                external.updatedAt(),
                resolveImages(local, external)
        );
    }

    public ShelterPetListResponse getAiSourceList(String apiKey, String region, String breed, String status, String sort, int page, int size) {
        aiService.verifyAiApiKey(apiKey);
        String cacheKey = String.join(":",
            "list",
            "v" + aiSourceCacheService.currentVersion(AI_CACHE_NAMESPACE),
            normalizeCacheValue(region),
            normalizeCacheValue(breed),
            normalizeCacheValue(status),
            normalizeCacheValue(sort),
            String.valueOf(page),
            String.valueOf(size)
        );
        return aiSourceCacheService.getOrLoad(
            cacheKey,
            Duration.ofSeconds(aiSourceCacheTtlSeconds),
            ShelterPetListResponse.class,
            () -> getShelterPetList(region, breed, status, sort, page, size)
        );
    }

    public ShelterPetDetailResponse getAiSourceDetail(String apiKey, String id) {
        aiService.verifyAiApiKey(apiKey);
        String cacheKey = String.join(":",
            "detail",
            "v" + aiSourceCacheService.currentVersion(AI_CACHE_NAMESPACE),
            normalizeCacheValue(id)
        );
        return aiSourceCacheService.getOrLoad(
            cacheKey,
            Duration.ofSeconds(aiSourceCacheTtlSeconds),
            ShelterPetDetailResponse.class,
            () -> getShelterPetDetail(id)
        );
    }

    public AiAnalysisResultCallbackResponse receiveAnalysisResult(String id, String apiKey, AiAnalysisResultCallbackRequest request) {
        AiAnalysisResultCallbackResponse response = aiService.saveShelterAnalysisResult(id, apiKey, request);
        aiSourceCacheService.bumpVersion(AI_CACHE_NAMESPACE);
        return response;
    }

    private ShelterPet getOrCreateShelterPet(String id) {
        return shelterPetRepository.findById(id)
                .orElseGet(() -> {
                    ShelterPublicApiClient.ShelterPublicApiAnimal external = shelterPublicApiClient.fetchShelterPet(id);
                    ShelterPet shelterPet = ShelterPet.builder()
                            .id(id)
                            .source(external.source())
                            .region(extractRegion(external))
                            .breed(firstNonBlank(external.kindName(), external.kindFullName(), "미상"))
                            .status(firstNonBlank(external.processState(), "UNKNOWN"))
                            .title(buildDefaultTitle(external))
                            .description(buildDefaultDescription(external))
                            .contactPhone(external.careTel())
                            .images(new ArrayList<>(external.imageUrls()))
                            .build();
                    return shelterPetRepository.save(shelterPet);
                });
    }

    private String resolveTitle(ShelterPet local, ShelterPublicApiClient.ShelterPublicApiAnimal external) {
        return firstNonBlank(local == null ? null : local.getTitle(), buildDefaultTitle(external));
    }

    private String resolveDescription(ShelterPet local, ShelterPublicApiClient.ShelterPublicApiAnimal external) {
        return firstNonBlank(local == null ? null : local.getDescription(), external.specialMark());
    }

    private String resolveRegionLabel(ShelterPet local, ShelterPublicApiClient.ShelterPublicApiAnimal external) {
        return firstNonBlank(local == null ? null : local.getRegion(), extractRegion(external));
    }

    private String resolveBreedLabel(ShelterPet local, ShelterPublicApiClient.ShelterPublicApiAnimal external) {
        return firstNonBlank(local == null ? null : local.getBreed(), external.kindName(), external.kindFullName(), "미상");
    }

    private String resolveStatusLabel(ShelterPet local, ShelterPublicApiClient.ShelterPublicApiAnimal external) {
        return firstNonBlank(local == null ? null : local.getStatus(), external.processState(), "UNKNOWN");
    }

    private String extractRegion(ShelterPublicApiClient.ShelterPublicApiAnimal external) {
        String region = firstNonBlank(external.organizationName(), external.careAddress());
        if (!StringUtils.hasText(region)) {
            return "미상";
        }
        return region;
    }

    private String buildDefaultTitle(ShelterPublicApiClient.ShelterPublicApiAnimal external) {
        String breed = firstNonBlank(external.kindFullName(), external.kindName(), "반려동물");
        String state = firstNonBlank(external.processState(), "보호");
        return breed + " " + state + " 공고";
    }

    private String buildDefaultDescription(ShelterPublicApiClient.ShelterPublicApiAnimal external) {
        return external.specialMark();
    }

    private List<String> resolveImages(ShelterPet local, ShelterPublicApiClient.ShelterPublicApiAnimal external) {
        if (local != null && local.getImages() != null && !local.getImages().isEmpty()) {
            return local.getImages();
        }
        return external.imageUrls();
    }

    private String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (StringUtils.hasText(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean matchesStatus(ShelterPublicApiClient.ShelterPublicApiAnimal item, String status) {
        if (!StringUtils.hasText(status)) {
            return true;
        }
        String normalized = status.trim().toLowerCase();
        String processState = firstNonBlank(item.processState(), "").toLowerCase();
        return switch (normalized) {
            case "open", "notice", "noticed" -> processState.contains("공고") || processState.contains("notice");
            case "protect", "protected", "resolved" -> processState.contains("보호") || processState.contains("종료") || processState.contains("returned") || processState.contains("protect");
            default -> processState.equals(normalized) || processState.contains(normalized);
        };
    }

    private boolean matchesRegion(ShelterPublicApiClient.ShelterPublicApiAnimal item, String region, boolean codeFilter) {
        if (!StringUtils.hasText(region) || codeFilter) {
            return true;
        }
        return containsIgnoreCase(item.organizationName(), region)
                || containsIgnoreCase(item.careAddress(), region)
                || containsIgnoreCase(item.careName(), region)
                || containsIgnoreCase(item.happenPlace(), region);
    }

    private boolean matchesBreed(ShelterPublicApiClient.ShelterPublicApiAnimal item, String breed, boolean codeFilter) {
        if (!StringUtils.hasText(breed) || codeFilter) {
            return true;
        }
        return containsIgnoreCase(item.kindName(), breed)
                || containsIgnoreCase(item.kindFullName(), breed);
    }

    private boolean containsIgnoreCase(String value, String keyword) {
        if (!StringUtils.hasText(value) || !StringUtils.hasText(keyword)) {
            return false;
        }
        return value.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
    }

    private boolean isCodeFilter(String value) {
        return StringUtils.hasText(value) && value.chars().allMatch(Character::isDigit);
    }

    private String normalizeTextFilter(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
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
        if (!StringUtils.hasText(value)) {
            return "_";
        }
        return value.trim();
    }
}
