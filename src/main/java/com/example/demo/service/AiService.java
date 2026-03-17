package com.example.demo.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiService {

    public Map<String, Object> uploadPhoto(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null) filename = "unknown";
        
        return Map.of(
                "photoId", "photo-" + System.currentTimeMillis(),
                "fileName", filename,
                "size", file.getSize(),
                "contentType", file.getContentType()
        );
    }

    public Map<String, Object> requestAnalysis(Map<String, Object> request) {
        return Map.of(
                "analysisId", "analysis-1", 
                "photoId", request.getOrDefault("photoId", "photo-1"), 
                "status", "PENDING"
        );
    }

    public Map<String, Object> getAnalysisResult(String analysisId) {
        return Map.of(
                "analysisId", analysisId, 
                "status", "SUCCESS", 
                "features", List.of("흰색", "소형견", "귀가 접힘")
        );
    }

    public Map<String, Object> getSimilarPosts(String analysisId) {
        return Map.of(
                "analysisId", analysisId, 
                "items", List.of("missing-post-3", "missing-post-4")
        );
    }

    public Map<String, Object> retryAnalysis(String analysisId) {
        return Map.of(
                "analysisId", analysisId, 
                "status", "RETRYING"
        );
    }
}
