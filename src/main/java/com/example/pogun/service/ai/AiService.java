package com.example.pogun.service.ai;

import com.example.pogun.dto.ai.AiAnalysisCreateResponse;
import com.example.pogun.dto.ai.AiAnalysisResultResponse;
import com.example.pogun.dto.ai.AiPhotoUploadResponse;
import com.example.pogun.dto.ai.AiRetryResponse;
import com.example.pogun.dto.ai.AiSimilarPostsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
/**
 * 도메인 비즈니스 로직을 담당하는 AiService이다.
 */

@Service
@RequiredArgsConstructor
public class AiService {

    // 현재 AI 도메인은 업로드/요청/결과 계약을 먼저 고정한 상태이며, 실제 분석 파이프라인은 추후 연결 예정이다.

    public AiPhotoUploadResponse uploadPhoto(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null) filename = "unknown";

        return new AiPhotoUploadResponse(
                "photo-" + System.currentTimeMillis(),
                filename,
                file.getSize(),
                file.getContentType()
        );
    }

    // 실제 큐 적재 대신 임시 analysis id 를 반환해 비동기 분석 API 계약만 먼저 유지한다.
    public AiAnalysisCreateResponse requestAnalysis(Map<String, Object> request) {
        return new AiAnalysisCreateResponse(
                "analysis-1",
                String.valueOf(request.getOrDefault("photoId", "photo-1")),
                "PENDING"
        );
    }

    public AiAnalysisResultResponse getAnalysisResult(String analysisId) {
        return new AiAnalysisResultResponse(
                analysisId,
                "SUCCESS",
                List.of("흰색", "소형견", "귀가 접힘")
        );
    }

    // 유사 공고 추천도 현재는 샘플 응답이며, 이후 분석 결과와 공고 매칭 로직으로 대체될 지점이다.
    public AiSimilarPostsResponse getSimilarPosts(String analysisId) {
        return new AiSimilarPostsResponse(
                analysisId,
                List.of("missing-post-3", "missing-post-4")
        );
    }

    public AiRetryResponse retryAnalysis(String analysisId) {
        return new AiRetryResponse(analysisId, "RETRYING");
    }
}
