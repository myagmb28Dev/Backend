package com.example.pogun.controller.shelterpet;
import com.example.pogun.dto.ai.AiAnalysisResultCallbackRequest;
import com.example.pogun.dto.ai.AiAnalysisResultCallbackResponse;
import com.example.pogun.dto.common.ApiResponse;
import com.example.pogun.dto.shelterpet.ShelterPetDetailResponse;
import com.example.pogun.dto.shelterpet.ShelterPetListResponse;
import com.example.pogun.dto.shelterpet.ShelterReferenceListResponse;
import com.example.pogun.service.shelterpet.ShelterPetService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
/**
 * HTTP/WebSocket 진입점을 담당하는 ShelterPetController이다.
 */

@RestController
@RequestMapping("/api/shelter")
@Tag(name = "Shelter", description = "전국 외부 유기동물 공고 API")
@RequiredArgsConstructor
public class ShelterPetController {

    private final ShelterPetService shelterPetService;

    @GetMapping
    @Operation(summary = "유기동물 공고 목록 조회", description = "전국 유기동물 공고 목록을 조회합니다. 필터와 정렬, 페이징을 지원합니다.")
    public ResponseEntity<ApiResponse<ShelterPetListResponse>> list(
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String breed,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "LATEST") String sort,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size
    ) {
        // 외부 공고는 현재 우리 DB에 동기화된 캐시 기준으로 필터링하고 응답한다.
        ShelterPetListResponse data = shelterPetService.getShelterPetList(region, breed, status, sort, page, size);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "전국 유기 동물 공고 조회 성공", data));
    }

    @GetMapping("/ai-source")
    @Operation(summary = "유기동물 공고 AI 목록 조회", description = "AI 서버가 API 키 헤더로 조회하는 유기동물 공고 목록입니다.")
    public ResponseEntity<ApiResponse<ShelterPetListResponse>> aiSourceList(
            @Parameter(name = "X-AI-API-KEY", description = "AI 서버 인증용 API 키 헤더", required = true, example = "your-ai-api-key")
            @RequestHeader(name = "X-AI-API-KEY", required = false) String apiKey,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String breed,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "LATEST") String sort,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size
    ) {
        ShelterPetListResponse data = shelterPetService.getAiSourceList(apiKey, region, breed, status, sort, page, size);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "유기 동물 공고 AI 목록 조회 성공", data));
    }

    @GetMapping("/sidos")
    @Operation(summary = "시도 목록 조회", description = "전국 시도 코드와 이름 목록을 조회합니다.")
    public ResponseEntity<ApiResponse<ShelterReferenceListResponse>> sidos() {
        ShelterReferenceListResponse data = shelterPetService.getSidoList();
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "시도 목록 조회 성공", data));
    }

    @GetMapping("/sigungus")
    @Operation(summary = "시군구 목록 조회", description = "선택한 시도에 속한 시군구 코드와 이름 목록을 조회합니다.")
    public ResponseEntity<ApiResponse<ShelterReferenceListResponse>> sigungus(@RequestParam String uprCd) {
        ShelterReferenceListResponse data = shelterPetService.getSigunguList(uprCd);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "시군구 목록 조회 성공", data));
    }

    @GetMapping("/shelters")
    @Operation(summary = "보호소 목록 조회", description = "선택한 시도와 시군구에 속한 보호소 목록을 조회합니다.")
    public ResponseEntity<ApiResponse<ShelterReferenceListResponse>> shelters(
            @RequestParam String uprCd,
            @RequestParam String orgCd
    ) {
        ShelterReferenceListResponse data = shelterPetService.getShelterList(uprCd, orgCd);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "보호소 목록 조회 성공", data));
    }

    @GetMapping("/breeds")
    @Operation(summary = "품종 목록 조회", description = "선택한 축종에 속한 품종 코드와 이름 목록을 조회합니다.")
    public ResponseEntity<ApiResponse<ShelterReferenceListResponse>> breeds(@RequestParam String upKindCd) {
        ShelterReferenceListResponse data = shelterPetService.getBreedList(upKindCd);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "품종 목록 조회 성공", data));
    }

    @GetMapping("/{id:\\d+}")
    @Operation(summary = "유기동물 공고 상세 조회", description = "외부 공고의 상세 정보를 조회합니다.")
    public ResponseEntity<ApiResponse<ShelterPetDetailResponse>> detail(@PathVariable String id) {
        ShelterPetDetailResponse data = shelterPetService.getShelterPetDetail(id);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "유기 동물 공고 상세 조회 성공", data));
    }

    @GetMapping("/{id:\\d+}/ai-source")
    @Operation(summary = "유기동물 공고 AI 상세 조회", description = "AI 서버가 API 키 헤더로 조회하는 유기동물 공고 상세 정보입니다.")
    public ResponseEntity<ApiResponse<ShelterPetDetailResponse>> aiSourceDetail(
            @PathVariable String id,
            @Parameter(name = "X-AI-API-KEY", description = "AI 서버 인증용 API 키 헤더", required = true, example = "your-ai-api-key")
            @RequestHeader(name = "X-AI-API-KEY", required = false) String apiKey
    ) {
        ShelterPetDetailResponse data = shelterPetService.getAiSourceDetail(apiKey, id);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "유기 동물 공고 AI 상세 조회 성공", data));
    }

    @PostMapping("/{id:\\d+}/analysis-result")
    @Operation(summary = "보호 공고 AI 분석 결과 수신", description = "FastAPI가 보호 공고 분석 결과를 콜백으로 전송합니다.")
    public ResponseEntity<ApiResponse<AiAnalysisResultCallbackResponse>> receiveAnalysisResult(
            @PathVariable String id,
            @Parameter(name = "X-AI-API-KEY", description = "AI 서버 인증용 API 키 헤더", required = true, example = "your-ai-api-key")
            @RequestHeader(name = "X-AI-API-KEY", required = false) String apiKey,
            @Valid @RequestBody AiAnalysisResultCallbackRequest request
    ) {
        AiAnalysisResultCallbackResponse data = shelterPetService.receiveAnalysisResult(id, apiKey, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED, "보호 공고 AI 분석 결과 수신 성공", data));
    }
}
