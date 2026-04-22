package com.example.pogun.service.ai;

import com.example.pogun.dto.ai.AiAnalysisResultCallbackRequest;
import com.example.pogun.dto.ai.AiAnalysisResultCallbackResponse;
import com.example.pogun.dto.common.ApiResponse.ApiException;
import com.example.pogun.entity.ai.AiAnalysis;
import com.example.pogun.entity.ai.enums.AiAnalysisStatus;
import com.example.pogun.entity.ai.enums.AiAnalysisTargetType;
import com.example.pogun.entity.missingpet.PetNotice;
import com.example.pogun.repository.ai.AiAnalysisRepository;
import com.example.pogun.repository.missingpet.PetNoticeRepository;
import com.example.pogun.repository.shelterpet.ShelterPetRepository;
import com.example.pogun.service.shelterpet.ShelterPublicApiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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

    public AiAnalysisResultCallbackResponse saveMissingPetAnalysisResult(String missingPetId, String apiKey, AiAnalysisResultCallbackRequest request) {
        PetNotice notice = petNoticeRepository.findById(parseNoticeId(missingPetId))
                .orElseThrow(() -> ApiException.notFound("NOTICE_NOT_FOUND", "실종 공고를 찾을 수 없습니다."));
        if (Boolean.TRUE.equals(notice.getHidden())) {
            throw ApiException.notFound("NOTICE_NOT_FOUND", "실종 공고를 찾을 수 없습니다.");
        }
        verifyAiApiKey(apiKey);
        return saveAnalysisResult(AiAnalysisTargetType.MISSING_PET, notice.getId().toString(), request);
    }

    public AiAnalysisResultCallbackResponse saveShelterAnalysisResult(String shelterId, String apiKey, AiAnalysisResultCallbackRequest request) {
        if (shelterPetRepository.findById(shelterId).isEmpty()) {
            shelterPublicApiClient.fetchShelterPet(shelterId);
        }
        verifyAiApiKey(apiKey);
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
        analysis.setSimilarNoticeIds(copyList(request.getSimilarNoticeIds()));

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

    private AiAnalysisStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            throw ApiException.badRequest("INVALID_AI_STATUS", "status는 필수입니다.");
        }
        try {
            return AiAnalysisStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("INVALID_AI_STATUS", "올바르지 않은 AI 분석 상태입니다.");
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
}
