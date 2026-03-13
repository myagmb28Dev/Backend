package com.example.demo.controller;

import java.util.List;
import java.util.Map;

import com.example.demo.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bookmarks")
@Tag(name = "Bookmarks", description = "즐겨찾기 관련 API")
public class BookmarkController {

    @PostMapping("/{noticeId}")
    @Operation(summary = "즐겨찾기 추가", description = "공고를 즐겨찾기에 추가합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> add(@PathVariable String noticeId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED, "공고 즐겨찾기 추가 성공", Map.of("noticeId", noticeId, "bookmarked", true)));
    }

    @DeleteMapping("/{noticeId}")
    @Operation(summary = "즐겨찾기 해제", description = "공고의 즐겨찾기를 해제합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> remove(@PathVariable String noticeId) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "공고 즐겨찾기 해제 성공", Map.of("noticeId", noticeId, "bookmarked", false)));
    }

    @GetMapping
    @Operation(summary = "내 즐겨찾기 조회", description = "내가 즐겨찾기한 공고 목록을 조회합니다.")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> list() {
        List<Map<String, Object>> data = List.of(
                Map.of("noticeId", "notice-1", "title", "말티즈 실종"),
                Map.of("noticeId", "notice-7", "title", "고양이 목격")
        );
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "내 즐겨찾기 목록 조회 성공", data));
    }
}


