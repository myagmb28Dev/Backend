package com.example.pogun.service.shelterpet;

import com.example.pogun.dto.ai.AiAnalysisResultCallbackRequest;
import com.example.pogun.dto.ai.AiAnalysisResultCallbackResponse;
import com.example.pogun.dto.shelterpet.ShelterPetDetailResponse;
import com.example.pogun.dto.shelterpet.ShelterPetListFiltersResponse;
import com.example.pogun.dto.shelterpet.ShelterPetListResponse;
import com.example.pogun.dto.shelterpet.ShelterReferenceItemResponse;
import com.example.pogun.dto.shelterpet.ShelterReferenceListResponse;
import com.example.pogun.dto.shelterpet.ShelterPetStatusResponse;
import com.example.pogun.dto.shelterpet.ShelterPetSummaryResponse;
import com.example.pogun.dto.shelterpet.ShelterPetUpdateResponse;
import com.example.pogun.dto.shelterpet.ShelterPetViewResponse;
import com.example.pogun.entity.shelterpet.ShelterPet;
import com.example.pogun.repository.shelterpet.ShelterPetRepository;
import com.example.pogun.service.ai.AiService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
/**
 * 도메인 비즈니스 로직을 담당하는 ShelterPetService이다.
 */

@Service
@RequiredArgsConstructor
public class ShelterPetService {

    private final ShelterPublicApiClient shelterPublicApiClient;
    private final ShelterPetRepository shelterPetRepository;
    private final AiService aiService;

    public ShelterPetListResponse getShelterPetList(String region, String breed, String status, String sort, int page, int size) {
        ShelterPublicApiClient.ShelterPublicApiPage result = shelterPublicApiClient.fetchShelterPets(region, breed, status, sort, page, size);
        List<ShelterPetSummaryResponse> items = result.items().stream()
                .map(item -> {
                    ShelterPet local = shelterPetRepository.findById(item.desertionNo()).orElse(null);
                    return new ShelterPetSummaryResponse(
                            item.desertionNo(),
                            resolveRegionLabel(local, item),
                            resolveBreedLabel(local, item),
                            resolveStatusLabel(local, item)
                    );
                })
                .toList();

        return new ShelterPetListResponse(
                "KOREA_ANIMAL_PROTECTION_API",
                new ShelterPetListFiltersResponse(region, breed, status, sort, page, size),
                items
        );
    }

    public ShelterPetDetailResponse getShelterPetDetail(String id) {
        ShelterPublicApiClient.ShelterPublicApiAnimal external = shelterPublicApiClient.fetchShelterPet(id);
        ShelterPet local = shelterPetRepository.findById(id).orElse(null);
        return new ShelterPetDetailResponse(
                id,
                resolveTitle(local, external),
                resolveDescription(local, external),
                local == null ? null : local.getRewardAmount(),
                firstNonBlank(local == null ? null : local.getContactPhone(), external.careTel()),
                resolveImages(local, external)
        );
    }

    public ShelterPetUpdateResponse updateShelterPet(String id, Map<String, Object> request) {
        ShelterPet shelterPet = getOrCreateShelterPet(id);
        if (request.containsKey("title")) {
            shelterPet.setTitle(toNullableString(request.get("title")));
        }
        if (request.containsKey("description")) {
            shelterPet.setDescription(toNullableString(request.get("description")));
        }
        if (request.containsKey("rewardAmount")) {
            shelterPet.setRewardAmount(request.get("rewardAmount") instanceof Number number ? number.intValue() : null);
        }
        if (request.containsKey("contactPhone")) {
            shelterPet.setContactPhone(toNullableString(request.get("contactPhone")));
        }
        if (request.containsKey("imageUrls")) {
            shelterPet.setImages(request.get("imageUrls") instanceof List<?> imageUrls
                    ? imageUrls.stream().map(String::valueOf).toList()
                    : new ArrayList<>());
        }
        ShelterPet saved = shelterPetRepository.save(shelterPet);
        return new ShelterPetUpdateResponse(
                id,
                true,
                saved.getTitle(),
                saved.getDescription(),
                saved.getRewardAmount(),
                saved.getContactPhone(),
                saved.getImages()
        );
    }

    public ShelterPetStatusResponse changeShelterPetStatus(String id, String status) {
        ShelterPet shelterPet = getOrCreateShelterPet(id);
        shelterPet.setStatus(status);
        shelterPetRepository.save(shelterPet);
        return new ShelterPetStatusResponse(id, shelterPet.getStatus());
    }

    public ShelterPetViewResponse increaseShelterPetView(String id) {
        ShelterPet shelterPet = getOrCreateShelterPet(id);
        shelterPet.setViewCount((shelterPet.getViewCount() == null ? 0 : shelterPet.getViewCount()) + 1);
        ShelterPet saved = shelterPetRepository.save(shelterPet);
        return new ShelterPetViewResponse(id, saved.getViewCount());
    }

    public ShelterPetListResponse getAiSourceList(String apiKey, String region, String breed, String status, String sort, int page, int size) {
        aiService.verifyAiApiKey(apiKey);
        return getShelterPetList(region, breed, status, sort, page, size);
    }

    public ShelterPetDetailResponse getAiSourceDetail(String apiKey, String id) {
        aiService.verifyAiApiKey(apiKey);
        return getShelterPetDetail(id);
    }

    public AiAnalysisResultCallbackResponse receiveAnalysisResult(String id, String apiKey, AiAnalysisResultCallbackRequest request) {
        return aiService.saveShelterAnalysisResult(id, apiKey, request);
    }

    public ShelterReferenceListResponse getSidoList() {
        return new ShelterReferenceListResponse(
                "KOREA_ANIMAL_PROTECTION_API",
                "SIDO",
                null,
                shelterPublicApiClient.fetchSido().stream()
                        .map(item -> new ShelterReferenceItemResponse(item.code(), item.name()))
                        .toList()
        );
    }

    public ShelterReferenceListResponse getSigunguList(String uprCd) {
        return new ShelterReferenceListResponse(
                "KOREA_ANIMAL_PROTECTION_API",
                "SIGUNGU",
                uprCd,
                shelterPublicApiClient.fetchSigungu(uprCd).stream()
                        .map(item -> new ShelterReferenceItemResponse(item.code(), item.name()))
                        .toList()
        );
    }

    public ShelterReferenceListResponse getShelterList(String uprCd, String orgCd) {
        return new ShelterReferenceListResponse(
                "KOREA_ANIMAL_PROTECTION_API",
                "SHELTER",
                orgCd,
                shelterPublicApiClient.fetchShelters(uprCd, orgCd).stream()
                        .map(item -> new ShelterReferenceItemResponse(item.code(), item.name()))
                        .toList()
        );
    }

    public ShelterReferenceListResponse getBreedList(String upKindCd) {
        return new ShelterReferenceListResponse(
                "KOREA_ANIMAL_PROTECTION_API",
                "KIND",
                upKindCd,
                shelterPublicApiClient.fetchKinds(upKindCd).stream()
                        .map(item -> new ShelterReferenceItemResponse(item.code(), item.name()))
                        .toList()
        );
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
        return firstNonBlank(local == null ? null : local.getDescription(), buildDefaultDescription(external));
    }

    private List<String> resolveImages(ShelterPet local, ShelterPublicApiClient.ShelterPublicApiAnimal external) {
        if (local != null && local.getImages() != null && !local.getImages().isEmpty()) {
            return local.getImages();
        }
        return external.imageUrls();
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
        List<String> lines = new ArrayList<>();
        addLine(lines, "공고번호", external.noticeNo());
        addLine(lines, "발견장소", external.happenPlace());
        addLine(lines, "특징", external.specialMark());
        addLine(lines, "보호소", external.careName());
        addLine(lines, "주소", external.careAddress());
        addLine(lines, "공고기간", joinDateRange(external.noticeStartDate(), external.noticeEndDate()));
        return String.join("\n", lines);
    }

    private String joinDateRange(String start, String end) {
        if (!StringUtils.hasText(start) && !StringUtils.hasText(end)) {
            return null;
        }
        if (!StringUtils.hasText(start)) {
            return end;
        }
        if (!StringUtils.hasText(end)) {
            return start;
        }
        return start + " ~ " + end;
    }

    private void addLine(List<String> lines, String label, String value) {
        if (StringUtils.hasText(value)) {
            lines.add(label + ": " + value);
        }
    }

    private String toNullableString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (StringUtils.hasText(candidate)) {
                return candidate;
            }
        }
        return null;
    }
}
