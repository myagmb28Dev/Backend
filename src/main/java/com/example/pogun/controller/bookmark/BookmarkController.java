package com.example.pogun.controller.bookmark;

import com.example.pogun.dto.common.ApiResponse;
import com.example.pogun.dto.bookmark.BookmarkActionResponse;
import com.example.pogun.dto.bookmark.BookmarkSummaryResponse;
import com.example.pogun.service.bookmark.BookmarkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
/**
 * HTTP/WebSocket 진입점을 담당하는 BookmarkController이다.
 */

@RestController
@RequestMapping("/api/bookmarks")
@Tag(name = "Bookmarks", description = "즐겨찾기 관련 API")
@RequiredArgsConstructor
public class BookmarkController {

    private final BookmarkService bookmarkService;

    @PostMapping("/{noticeId}")
    @Operation(summary = "즐겨찾기 추가", description = "공고를 즐겨찾기에 추가합니다.")
    public ResponseEntity<ApiResponse<BookmarkActionResponse>> add(@PathVariable String noticeId) {
        BookmarkActionResponse data = bookmarkService.addBookmark(noticeId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED, "공고 즐겨찾기 추가 성공", data));
    }

    @DeleteMapping("/{noticeId}")
    @Operation(summary = "즐겨찾기 해제", description = "공고의 즐겨찾기를 해제합니다.")
    public ResponseEntity<ApiResponse<BookmarkActionResponse>> remove(@PathVariable String noticeId) {
        BookmarkActionResponse data = bookmarkService.removeBookmark(noticeId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "공고 즐겨찾기 해제 성공", data));
    }

    @GetMapping
    @Operation(summary = "내 즐겨찾기 조회", description = "내가 즐겨찾기한 공고 목록을 조회합니다.")
    public ResponseEntity<ApiResponse<List<BookmarkSummaryResponse>>> list() {
        // 목록 응답은 화면 카드에 바로 쓰일 수 있도록 공고 요약 형태로 정리되어 내려간다.
        List<BookmarkSummaryResponse> data = bookmarkService.getMyBookmarks();
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "내 즐겨찾기 목록 조회 성공", data));
    }
}
