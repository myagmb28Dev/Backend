package com.example.demo.controller;

import java.util.Map;

import com.example.demo.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Reports", description = "게시글 신고 API")
public class ReportController {

    @PostMapping("/api/reports")
    @Operation(summary = "게시글 신고 접수", description = "게시글에 대한 신고를 접수합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> report(@RequestBody Map<String, Object> request) {
        Map<String, Object> data = Map.of(
                "id", "report-1",
                "status", "RECEIVED",
                "targetType", request.getOrDefault("targetType", "COMMUNITY_POST"),
                "targetId", request.getOrDefault("targetId", "post-1")
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED, "신고 접수 성공", data));
    }

}



