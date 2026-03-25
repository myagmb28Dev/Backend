package com.example.pogun.controller.report;

import com.example.pogun.dto.common.ApiResponse;
import com.example.pogun.dto.report.ReportCreateRequest;
import com.example.pogun.dto.report.ReportResponse;
import com.example.pogun.service.report.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
/**
 * HTTP/WebSocket 진입점을 담당하는 ReportController이다.
 */

@RestController
@RequestMapping("/api/reports")
@Tag(name = "Reports", description = "게시글 신고 API")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PostMapping
    @Operation(summary = "게시글 신고 접수", description = "게시글에 대한 신고를 접수합니다.")
    public ResponseEntity<ApiResponse<ReportResponse>> report(@Valid @RequestBody ReportCreateRequest request) {
        // 신고 접수 시 대상 존재 여부와 중복 신고 여부를 서비스에서 함께 검증한다.
        ReportResponse data = reportService.createReport(request.toRequestMap());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED, "신고 접수 성공", data));
    }
}