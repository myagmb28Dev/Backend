package com.example.pogun.controller.missingpet;
import com.example.pogun.dto.ai.AiAnalysisResultCallbackRequest;
import com.example.pogun.dto.ai.AiAnalysisResultCallbackResponse;
import com.example.pogun.dto.ai.SimilarNoticeListResponse;
import com.example.pogun.dto.common.ApiResponse;
import com.example.pogun.dto.missingpet.MissingPetCreateRequest;
import com.example.pogun.dto.missingpet.MissingPetDetailResponse;
import com.example.pogun.dto.missingpet.MissingPetListResponse;
import com.example.pogun.dto.missingpet.MissingPetStatusUpdateRequest;
import com.example.pogun.dto.missingpet.MissingPetUpdateRequest;
import com.example.pogun.dto.missingpet.MissingPetViewResponse;
import com.example.pogun.service.missingpet.MissingPetService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
/**
 * HTTP/WebSocket 진입점을 담당하는 MissingPetController이다.
 */

@RestController
@RequestMapping("/api/missing-pets")
@Tag(name = "Missing Pets", description = "실종 반려동물 공고 API")
@RequiredArgsConstructor
public class MissingPetController {

    private final MissingPetService missingPetService;

    @GetMapping
    @Operation(summary = "실종 동물 공고 목록 조회", description = "사용자가 작성한 실종 동물 공고 목록을 조회합니다.")
    public ResponseEntity<ApiResponse<MissingPetListResponse>> list(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String breed,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false, defaultValue = "false") boolean mineOnly,
            @RequestParam(required = false, defaultValue = "createdAt,desc") String sort,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size
    ) {
        // 목록 API는 필터와 정렬을 그대로 전달하고, 서비스가 최종 조회 규칙과 페이지 응답을 만든다.
        MissingPetListResponse result = missingPetService.getMissingPetList(query, region, breed, status, from, to, mineOnly, sort, page, size);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "실종 동물 공고 목록 조회 성공", result));
    }

    @GetMapping("/search")
    @Operation(summary = "실종 공고 검색", description = "제목과 특징/설명 기준으로 실종 공고를 검색합니다.")
    public ResponseEntity<ApiResponse<MissingPetListResponse>> search(
            @RequestParam String query,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String breed,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false, defaultValue = "createdAt,desc") String sort,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size
    ) {
        MissingPetListResponse result = missingPetService.searchMissingPets(query, region, breed, status, from, to, sort, page, size);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "실종 공고 검색 성공", result));
    }

    @GetMapping("/ai-source")
    @Operation(summary = "실종 동물 공고 AI 목록 조회", description = "AI 서버가 API 키 헤더로 조회하는 실종 동물 공고 목록입니다.")
    public ResponseEntity<ApiResponse<MissingPetListResponse>> aiSourceList(
            @Parameter(name = "X-AI-API-KEY", description = "AI 서버 인증용 API 키 헤더", required = true, example = "your-ai-api-key")
            @RequestHeader(name = "X-AI-API-KEY", required = false) String apiKey,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String breed,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false, defaultValue = "createdAt,desc") String sort,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size
    ) {
        MissingPetListResponse result = missingPetService.getAiSourceList(apiKey, query, region, breed, status, from, to, sort, page, size);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "실종 동물 공고 AI 목록 조회 성공", result));
    }

    @PostMapping
    @Operation(summary = "실종 공고 생성", description = "사용자가 새로운 실종 동물 공고를 생성합니다.")
    public ResponseEntity<ApiResponse<MissingPetDetailResponse>> create(@Valid @RequestBody MissingPetCreateRequest request) {
        MissingPetDetailResponse data = missingPetService.createMissingPet(request.toRequestMap());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED, "행방불명 공고 생성 성공", data));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "실종 공고 생성(파일 첨부)", description = "multipart/form-data 요청으로 `request` JSON과 `images` 파일 배열을 함께 받아 실종 공고와 S3 이미지를 저장합니다.")
    public ResponseEntity<ApiResponse<MissingPetDetailResponse>> createWithFiles(
            @Valid @RequestPart("request") MissingPetCreateRequest request,
            @RequestPart(value = "images", required = false) List<MultipartFile> images
    ) {
        MissingPetDetailResponse data = missingPetService.createMissingPet(request.toRequestMap(), images);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED, "행방불명 공고 생성 성공", data));
    }

    @GetMapping("/{missingPetId}")
    @Operation(summary = "실종 공고 상세 조회", description = "실종 동물 공고의 상세 정보를 조회합니다.")
    public ResponseEntity<ApiResponse<MissingPetDetailResponse>> detail(@PathVariable String missingPetId) {
        MissingPetDetailResponse data = missingPetService.getMissingPetDetail(missingPetId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "실종 공고 상세 조회 성공", data));
    }

    @GetMapping("/{missingPetId}/ai-source")
    @Operation(summary = "실종 공고 AI 상세 조회", description = "AI 서버가 API 키 헤더로 조회하는 실종 공고 상세 정보입니다.")
    public ResponseEntity<ApiResponse<MissingPetDetailResponse>> aiSourceDetail(
            @PathVariable String missingPetId,
            @Parameter(name = "X-AI-API-KEY", description = "AI 서버 인증용 API 키 헤더", required = true, example = "your-ai-api-key")
            @RequestHeader(name = "X-AI-API-KEY", required = false) String apiKey
    ) {
        MissingPetDetailResponse data = missingPetService.getAiSourceDetail(apiKey, missingPetId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "실종 공고 AI 상세 조회 성공", data));
    }

    @GetMapping("/{missingPetId}/similar-notices")
    @Operation(summary = "실종 공고 유사 공고 조회", description = "실종 공고 기준 최신 AI 유사 공고 목록을 조회합니다.")
    public ResponseEntity<ApiResponse<SimilarNoticeListResponse>> getSimilarNotices(@PathVariable String missingPetId) {
        SimilarNoticeListResponse data = missingPetService.getSimilarNotices(missingPetId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "실종 공고 유사 공고 조회 성공", data));
    }

    @PostMapping("/{missingPetId}/analysis-result")
    @Operation(summary = "실종 공고 AI 분석 결과 수신", description = "FastAPI가 실종 공고 분석 결과를 콜백으로 전송합니다.")
    public ResponseEntity<ApiResponse<AiAnalysisResultCallbackResponse>> receiveAnalysisResult(
            @PathVariable String missingPetId,
            @Parameter(name = "X-AI-API-KEY", description = "AI 서버 인증용 API 키 헤더", required = true, example = "your-ai-api-key")
            @RequestHeader(name = "X-AI-API-KEY", required = false) String apiKey,
            @Valid @RequestBody AiAnalysisResultCallbackRequest request
    ) {
        AiAnalysisResultCallbackResponse data = missingPetService.receiveAnalysisResult(missingPetId, apiKey, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED, "실종 공고 AI 분석 결과 수신 성공", data));
    }

    @PatchMapping("/{missingPetId}")
    @Operation(summary = "실종 공고 수정", description = "실종 동물 공고를 수정합니다.")
    public ResponseEntity<ApiResponse<MissingPetDetailResponse>> update(
            @PathVariable String missingPetId,
            @Valid @RequestBody MissingPetUpdateRequest request
    ) {
        MissingPetDetailResponse data = missingPetService.updateMissingPet(missingPetId, request.toRequestMap());
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "실종 공고 수정 성공", data));
    }

    @PatchMapping(value = "/{missingPetId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "실종 공고 수정(파일 첨부)", description = "multipart/form-data 요청으로 `request` JSON과 `images` 파일 배열을 함께 받아 실종 공고와 S3 이미지를 수정합니다.")
    public ResponseEntity<ApiResponse<MissingPetDetailResponse>> updateWithFiles(
            @PathVariable String missingPetId,
            @Valid @RequestPart("request") MissingPetUpdateRequest request,
            @RequestPart(value = "images", required = false) List<MultipartFile> images
    ) {
        MissingPetDetailResponse data = missingPetService.updateMissingPet(missingPetId, request.toRequestMap(), images);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "실종 공고 수정 성공", data));
    }

    @PatchMapping("/{missingPetId}/status")
    @Operation(summary = "실종 공고 상태 변경", description = "실종 동물 행방불명 공고의 상태를 변경합니다.")
    public ResponseEntity<ApiResponse<MissingPetDetailResponse>> changeStatus(
            @PathVariable String missingPetId,
            @Valid @RequestBody MissingPetStatusUpdateRequest request
    ) {
        // 상태 변경은 작성자 권한 검증과 즐겨찾기 사용자 알림까지 함께 연결되는 진입점이다.
        MissingPetDetailResponse data = missingPetService.changeMissingPetStatus(missingPetId, request.getStatus());
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "실종 공고 상태 변경 성공", data));
    }

    @DeleteMapping("/{missingPetId}")
    @Operation(summary = "실종 공고 삭제", description = "작성자가 실종 공고와 연결된 DM 방을 함께 삭제합니다.")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String missingPetId) {
        missingPetService.deleteMissingPet(missingPetId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "실종 공고 삭제 성공", null));
    }

    @PostMapping("/{missingPetId}/view")
    @Operation(summary = "실종 공고 조회수 증가", description = "실종 동물 공고의 조회수를 증가시킵니다.")
    public ResponseEntity<ApiResponse<MissingPetViewResponse>> increaseView(@PathVariable String missingPetId) {
        MissingPetViewResponse data = missingPetService.increaseMissingPetView(missingPetId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "조회수 증가 처리 완료", data));
    }

}
