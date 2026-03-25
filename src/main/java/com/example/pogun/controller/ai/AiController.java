package com.example.pogun.controller.ai;

import com.example.pogun.dto.ai.AiAnalysisCreateResponse;
import com.example.pogun.dto.ai.AiAnalysisRequest;
import com.example.pogun.dto.ai.AiAnalysisResultResponse;
import com.example.pogun.dto.ai.AiPhotoUploadResponse;
import com.example.pogun.dto.ai.AiRetryResponse;
import com.example.pogun.dto.ai.AiSimilarPostsResponse;
import com.example.pogun.dto.common.ApiResponse;
import com.example.pogun.service.ai.AiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
/**
 * HTTP/WebSocket 진입점을 담당하는 AiController이다.
 */

@RestController
@RequestMapping("/api/ai")
@Tag(name = "AI", description = "이미지 분석 AI API")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;

    @PostMapping(value = "/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "사진 업로드", description = "분석용 사진을 multipart/form-data로 업로드합니다. 허용 확장자: .png, .jpg; 최대 5MB")
    public ResponseEntity<ApiResponse<?>> uploadPhoto(
            @RequestPart("file") MultipartFile file
    ) {
        // 서비스에 넘기기 전에 파일 유효성을 컨트롤러에서 빠르게 차단해 클라이언트가 원인을 바로 알 수 있게 한다.
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.fail(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "파일이 첨부되지 않았습니다.", null));
        }

        long maxBytes = 5L * 1024L * 1024L;
        if (file.getSize() > maxBytes) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.fail(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "파일 크기가 5MB를 초과합니다.", Map.of("maxSizeBytes", maxBytes, "size", file.getSize())));
        }

        String filename = file.getOriginalFilename();
        String ext = "";
        if (filename != null && filename.contains(".")) {
            ext = filename.substring(filename.lastIndexOf('.')).toLowerCase(Locale.ROOT);
        }
        Set<String> allowed = Set.of(".png", ".jpg", ".jpeg");
        if (!allowed.contains(ext)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.fail(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "허용되지 않는 파일 확장자입니다.", Map.of("allowed", allowed, "ext", ext)));
        }

        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        Set<String> allowedContentTypes = Set.of(MediaType.IMAGE_PNG_VALUE, MediaType.IMAGE_JPEG_VALUE);
        if (!allowedContentTypes.contains(contentType)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.fail(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "허용되지 않는 파일 형식입니다.", Map.of("allowedContentTypes", allowedContentTypes, "contentType", contentType)));
        }

        try {
            if (!matchesImageSignature(file, contentType)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.fail(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "파일 내용이 유효한 이미지가 아닙니다.", Map.of("contentType", contentType)));
            }
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.fail(HttpStatus.BAD_REQUEST, "INVALID_FILE", "업로드 파일을 읽을 수 없습니다.", null));
        }

        AiPhotoUploadResponse resp = aiService.uploadPhoto(file);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(HttpStatus.CREATED, "분석용 사진 업로드 성공", resp));
    }

    @PostMapping("/analysis")
    @Operation(summary = "사진 분석 요청", description = "업로드된 사진의 분석을 요청합니다.")
    public ResponseEntity<ApiResponse<AiAnalysisCreateResponse>> requestAnalysis(@Valid @RequestBody AiAnalysisRequest request) {
        // 업로드된 photoId를 기준으로 분석 작업을 생성하고, 이후 조회 API가 같은 analysisId를 사용한다.
        AiAnalysisCreateResponse resp = aiService.requestAnalysis(request.toRequestMap());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                HttpStatus.CREATED,
                "사진 분석 요청 성공",
                resp
        ));
    }

    @GetMapping("/analysis/{analysisId}")
    @Operation(summary = "분석 결과 조회", description = "사진 분석 결과를 조회합니다.")
    public ResponseEntity<ApiResponse<AiAnalysisResultResponse>> getAnalysis(@PathVariable String analysisId) {
        AiAnalysisResultResponse resp = aiService.getAnalysisResult(analysisId);
        return ResponseEntity.ok(ApiResponse.success(
                HttpStatus.OK,
                "분석 결과 조회 성공",
                resp
        ));
    }

    @GetMapping("/analysis/{analysisId}/similar-posts")
    @Operation(summary = "유사 공고 추천", description = "분석 결과를 기반으로 유사 공고를 추천합니다.")
    public ResponseEntity<ApiResponse<AiSimilarPostsResponse>> getSimilarPosts(@PathVariable String analysisId) {
        AiSimilarPostsResponse resp = aiService.getSimilarPosts(analysisId);
        return ResponseEntity.ok(ApiResponse.success(
                HttpStatus.OK,
                "유사 공고 추천 조회 성공",
                resp
        ));
    }

    @PostMapping("/analysis/{analysisId}/retry")
    @Operation(summary = "분석 재시도", description = "분석 실패 시 재시도 요청을 보냅니다.")
    public ResponseEntity<ApiResponse<AiRetryResponse>> retryAnalysis(@PathVariable String analysisId) {
        AiRetryResponse resp = aiService.retryAnalysis(analysisId);
        return ResponseEntity.ok(ApiResponse.success(
                HttpStatus.OK,
                "분석 재시도 요청 성공",
                resp
        ));
    }

    private boolean matchesImageSignature(MultipartFile file, String contentType) throws IOException {
        byte[] header = file.getInputStream().readNBytes(8);
        if (MediaType.IMAGE_PNG_VALUE.equals(contentType)) {
            return header.length >= 8
                    && (header[0] & 0xFF) == 0x89
                    && header[1] == 0x50
                    && header[2] == 0x4E
                    && header[3] == 0x47
                    && header[4] == 0x0D
                    && header[5] == 0x0A
                    && header[6] == 0x1A
                    && header[7] == 0x0A;
        }
        if (MediaType.IMAGE_JPEG_VALUE.equals(contentType)) {
            return header.length >= 3
                    && (header[0] & 0xFF) == 0xFF
                    && (header[1] & 0xFF) == 0xD8
                    && (header[2] & 0xFF) == 0xFF;
        }
        return false;
    }
}