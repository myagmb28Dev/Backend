package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@Tag(name = "Reports", description = "게시글 신고 API")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PostMapping("/api/reports")
    @Operation(summary = "게시글 신고 접수", description = "게시글에 대한 신고를 접수합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> report(@RequestBody Map<String, Object> request) {
        Map<String, Object> data = reportService.createReport(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED, "신고 접수 성공", data));
    }

}



