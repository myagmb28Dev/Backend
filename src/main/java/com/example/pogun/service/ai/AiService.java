package com.example.pogun.service.ai;

import com.example.pogun.dto.ai.AiAnalysisResultCallbackRequest;
import com.example.pogun.dto.ai.AiAnalysisResultCallbackResponse;
import com.example.pogun.dto.ai.MissingPetAnalysisRequestResponse;
import com.example.pogun.dto.ai.SimilarNoticeItemResponse;
import com.example.pogun.dto.ai.SimilarNoticeListResponse;
import com.example.pogun.dto.common.ApiResponse.ApiException;
import com.example.pogun.entity.ai.AiAnalysis;
import com.example.pogun.entity.ai.enums.AiAnalysisStatus;
import com.example.pogun.entity.ai.enums.AiAnalysisTargetType;
import com.example.pogun.entity.missingpet.PetNotice;
import com.example.pogun.entity.user.User;
import com.example.pogun.repository.ai.AiAnalysisRepository;
import com.example.pogun.repository.missingpet.PetNoticeRepository;
import com.example.pogun.repository.shelterpet.ShelterPetRepository;
import com.example.pogun.service.shelterpet.ShelterPublicApiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * AI 분석 결과 수신과 후처리 로직이 붙을 자리이다.
 */
@Service
@RequiredArgsConstructor
public class AiService {

    private final AiAnalysisRepository aiAnalysisRepository;
    private final PetNoticeRepository petNoticeRepository;
    private final ShelterPetRepository shelterPetRepository;
    private final ShelterPublicApiClient shelterPublicApiClient;

    @Value("${app.ai.api-key:}")
    private String aiApiKey;

    public MissingPetAnalysisRequestResponse createMissingPetAnalysisRequest(
            User author,
            PetNotice notice,
            int remainingCredits,
            int appliedMaxDescriptionLength,
            String purchaseId,
            String productId
    ) {
        AiAnalysis latest = aiAnalysisRepository.findTopByTargetTypeAndTargetIdOrderByCreatedAtDesc(
                AiAnalysisTargetType.MISSING_PET,
                notice.getId().toString()
        ).orElse(null);
        if (latest != null && (latest.getStatus() == AiAnalysisStatus.PENDING || latest.getStatus() == AiAnalysisStatus.RETRYING)) {
            throw ApiException.conflict("ANALYSIS_ALREADY_PENDING", "이미 진행 중인 AI 분석 요청이 있습니다.");
        }

        AiAnalysis analysis = aiAnalysisRepository.save(AiAnalysis.builder()
                .author(author)
                .targetType(AiAnalysisTargetType.MISSING_PET)
                .targetId(notice.getId().toString())
                .status(AiAnalysisStatus.PENDING)
                .provider("MANUAL_REQUEST")
                .features(new ArrayList<>())
                .similarNoticeIds(new ArrayList<>())
                .build());

        return new MissingPetAnalysisRequestResponse(
                analysis.getId(),
                notice.getId(),
                analysis.getStatus().name(),
                remainingCredits,
                appliedMaxDescriptionLength,
                purchaseId,
                productId
        );
    }

    public AiAnalysisResultCallbackResponse saveMissingPetAnalysisResult(String missingPetId, String apiKey, AiAnalysisResultCallbackRequest request) {
        verifyAiApiKey(apiKey);
        PetNotice notice = petNoticeRepository.findById(parseNoticeId(missingPetId))
                .orElseThrow(() -> ApiException.notFound("NOTICE_NOT_FOUND", "실종 공고를 찾을 수 없습니다."));
        if (Boolean.TRUE.equals(notice.getHidden())) {
            throw ApiException.notFound("NOTICE_NOT_FOUND", "실종 공고를 찾을 수 없습니다.");
        }
        return saveAnalysisResult(AiAnalysisTargetType.MISSING_PET, notice.getId().toString(), request);
    }

    public AiAnalysisResultCallbackResponse saveShelterAnalysisResult(String shelterId, String apiKey, AiAnalysisResultCallbackRequest request) {
        verifyAiApiKey(apiKey);
        if (shelterPetRepository.findById(shelterId).isEmpty()) {
            shelterPublicApiClient.fetchShelterPet(shelterId);
        }
        return saveAnalysisResult(AiAnalysisTargetType.SHELTER, shelterId, request);
    }

    private AiAnalysisResultCallbackResponse saveAnalysisResult(AiAnalysisTargetType targetType, String targetId, AiAnalysisResultCallbackRequest request) {
        AiAnalysis analysis = aiAnalysisRepository.findTopByTargetTypeAndTargetIdOrderByCreatedAtDesc(targetType, targetId)
                .orElseGet(() -> AiAnalysis.builder()
                        .targetType(targetType)
                        .targetId(targetId)
                        .features(new ArrayList<>())
                        .similarNoticeIds(new ArrayList<>())
                        .build());

        analysis.setStatus(parseStatus(request.getStatus()));
        analysis.setDetectedBreed(blankToNull(request.getBreed()));
        analysis.setDetectedColor(blankToNull(request.getColor()));
        analysis.setConfidence(request.getConfidence());
        analysis.setProvider(blankToNull(request.getProvider()));
        analysis.setErrorMessage(blankToNull(request.getErrorMessage()));
        analysis.setCompletedAt(request.getAnalyzedAt() == null ? Instant.now() : request.getAnalyzedAt());
        analysis.setFeatures(copyList(request.getFeatures()));
        analysis.setSimilarNoticeIds(sanitizeSimilarNoticeIds(targetType, targetId, request.getSimilarNoticeIds()));

        AiAnalysis saved = aiAnalysisRepository.save(analysis);
        return new AiAnalysisResultCallbackResponse(
                saved.getId().toString(),
                saved.getTargetType().name(),
                saved.getTargetId(),
                saved.getStatus().name()
        );
    }

    public void verifyAiApiKey(String apiKey) {
        if (aiApiKey == null || aiApiKey.isBlank()) {
            throw ApiException.internal("AI_API_KEY_NOT_CONFIGURED", "AI API 키가 설정되지 않았습니다.");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw ApiException.unauthorized("AI_API_UNAUTHORIZED", "AI API 키가 필요합니다.");
        }
        if (!aiApiKey.equals(apiKey.trim())) {
            throw ApiException.forbidden("AI_API_FORBIDDEN", "AI API 키가 올바르지 않습니다.");
        }
    }

    public SimilarNoticeListResponse getLatestSimilarNotices(String targetType, String targetId) {
        AiAnalysisTargetType resolvedTargetType = parseTargetType(targetType);
        String resolvedTargetId = normalizeAndValidateTargetId(resolvedTargetType, targetId);
        AiAnalysis analysis = aiAnalysisRepository.findTopByTargetTypeAndTargetIdOrderByCreatedAtDesc(
                        resolvedTargetType,
                        resolvedTargetId
                )
                .orElse(null);
        List<String> similarIds = analysis == null ? List.of() : copyList(analysis.getSimilarNoticeIds());
        List<SimilarNoticeItemResponse> items = similarIds.stream()
                .map(id -> new SimilarNoticeItemResponse(id, inferNoticeType(id), inferDetailPath(id)))
                .toList();
        return new SimilarNoticeListResponse(
                resolvedTargetType.name(),
                resolvedTargetId,
                items.size(),
                items
        );
    }

    private AiAnalysisStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return AiAnalysisStatus.FAILED;
        }
        try {
            return AiAnalysisStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return AiAnalysisStatus.FAILED;
        }
    }

    private java.util.UUID parseNoticeId(String value) {
        try {
            return java.util.UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("INVALID_NOTICE_ID", "올바르지 않은 공고 ID 형식입니다.");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private List<String> copyList(List<String> values) {
        return values == null ? List.of() : values.stream()
                .filter(item -> item != null && !item.isBlank())
                .map(String::trim)
                .toList();
    }

    private List<String> sanitizeSimilarNoticeIds(AiAnalysisTargetType targetType, String targetId, List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> filtered = new LinkedHashSet<>();
        for (String raw : values) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String id = raw.trim();
            if (id.matches("^[0-9]+$")) {
                if (shelterPetRepository.findById(id).isPresent()) {
                    filtered.add(id);
                }
                continue;
            }
            if (id.matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")) {
                UUID noticeId = parseNoticeId(id);
                PetNotice notice = petNoticeRepository.findById(noticeId).orElse(null);
                if (notice == null || Boolean.TRUE.equals(notice.getHidden())) {
                    continue;
                }
                if (targetType == AiAnalysisTargetType.MISSING_PET && targetId.equalsIgnoreCase(id)) {
                    continue;
                }
                filtered.add(id);
            }
        }
        return new ArrayList<>(filtered);
    }

    private AiAnalysisTargetType parseTargetType(String targetType) {
        if (targetType == null || targetType.isBlank()) {
            throw ApiException.badRequest("INVALID_TARGET_TYPE", "targetType은 필수입니다.");
        }
        try {
            return AiAnalysisTargetType.valueOf(targetType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("INVALID_TARGET_TYPE", "지원하지 않는 targetType입니다.");
        }
    }

    private String normalizeAndValidateTargetId(AiAnalysisTargetType targetType, String targetId) {
        if (targetId == null || targetId.isBlank()) {
            throw ApiException.badRequest("INVALID_TARGET_ID", "targetId는 필수입니다.");
        }
        String normalized = targetId.trim();
        switch (targetType) {
            case MISSING_PET -> {
                UUID noticeId = parseNoticeId(normalized);
                PetNotice notice = petNoticeRepository.findById(noticeId)
                        .orElseThrow(() -> ApiException.notFound("NOTICE_NOT_FOUND", "실종 공고를 찾을 수 없습니다."));
                if (Boolean.TRUE.equals(notice.getHidden())) {
                    throw ApiException.notFound("NOTICE_NOT_FOUND", "실종 공고를 찾을 수 없습니다.");
                }
                return notice.getId().toString();
            }
            case SHELTER -> {
                if (shelterPetRepository.findById(normalized).isEmpty()) {
                    shelterPublicApiClient.fetchShelterPet(normalized);
                }
                return normalized;
            }
            default -> throw ApiException.badRequest("INVALID_TARGET_TYPE", "지원하지 않는 targetType입니다.");
        }
    }

    private String inferNoticeType(String id) {
        if (id == null || id.isBlank()) {
            return "UNKNOWN";
        }
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        if (normalized.matches("^[0-9]+$")) {
            return "SHELTER";
        }
        if (normalized.matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")) {
            return "MISSING_PET";
        }
        return "UNKNOWN";
    }

    private String inferDetailPath(String id) {
        return switch (inferNoticeType(id)) {
            case "SHELTER" -> "/api/shelter/" + id;
            case "MISSING_PET" -> "/api/missing-pets/" + id;
            default -> null;
        };
    }
}
