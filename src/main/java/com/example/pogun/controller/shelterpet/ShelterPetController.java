package com.example.pogun.controller.shelterpet;

import com.example.pogun.dto.common.ApiResponse;
import com.example.pogun.dto.shelterpet.ShelterPetDetailResponse;
import com.example.pogun.dto.shelterpet.ShelterPetImageAnalysisResponse;
import com.example.pogun.dto.shelterpet.ShelterPetListResponse;
import com.example.pogun.dto.shelterpet.ShelterPetStatusResponse;
import com.example.pogun.dto.shelterpet.ShelterPetStatusUpdateRequest;
import com.example.pogun.dto.shelterpet.ShelterPetUpdateRequest;
import com.example.pogun.dto.shelterpet.ShelterPetUpdateResponse;
import com.example.pogun.dto.shelterpet.ShelterPetViewResponse;
import com.example.pogun.service.shelterpet.ShelterPetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

    @GetMapping("/{id}")
    @Operation(summary = "유기동물 공고 상세 조회", description = "외부 공고의 상세 정보를 조회합니다.")
    public ResponseEntity<ApiResponse<ShelterPetDetailResponse>> detail(@PathVariable String id) {
        ShelterPetDetailResponse data = shelterPetService.getShelterPetDetail(id);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "유기 동물 공고 상세 조회 성공", data));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "외부 공고 수정", description = "외부 공고 정보를 수정합니다. (관리자 전용)")
    public ResponseEntity<ApiResponse<ShelterPetUpdateResponse>> update(@PathVariable String id, @Valid @RequestBody ShelterPetUpdateRequest request) {
        ShelterPetUpdateResponse data = shelterPetService.updateShelterPet(id, request.toRequestMap());
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "외부 공고 수정 성공", data));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "외부 공고 상태 변경", description = "외부 공고의 상태를 변경합니다. (관리자 전용)")
    public ResponseEntity<ApiResponse<ShelterPetStatusResponse>> changeStatus(@PathVariable String id, @Valid @RequestBody ShelterPetStatusUpdateRequest request) {
        ShelterPetStatusResponse data = shelterPetService.changeShelterPetStatus(id, request.getStatus());
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "외부 공고 상태 변경 성공", data));
    }

    @PostMapping("/{id}/views")
    @Operation(summary = "외부 공고 조회수 증가", description = "외부 공고의 조회수를 증가시킵니다.")
    public ResponseEntity<ApiResponse<ShelterPetViewResponse>> increaseView(@PathVariable String id) {
        ShelterPetViewResponse data = shelterPetService.increaseShelterPetView(id);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "조회수 증가 처리 완료", data));
    }

    @PostMapping("/{id}/analyze-image")
    @Operation(summary = "외부 공고 사진 분석 요청", description = "공고에 첨부된 사진의 특징 분석을 요청합니다.")
    public ResponseEntity<ApiResponse<ShelterPetImageAnalysisResponse>> analyzeImage(@PathVariable String id) {
        // 분석 결과는 해당 외부 공고 레코드에 저장해 이후 상세 화면이나 추천 로직에서 재사용한다.
        ShelterPetImageAnalysisResponse data = shelterPetService.analyzeShelterPetImage(id);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "동물 사진 특징 분석 완료", data));
    }
}
