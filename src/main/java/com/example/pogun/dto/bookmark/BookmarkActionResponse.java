package com.example.pogun.dto.bookmark;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
/**
 * API 요청/응답 데이터 전송 객체인 BookmarkActionResponse이다.
 */

@Schema(description = "즐겨찾기 추가/해제 응답")
public record BookmarkActionResponse(UUID bookmarkId, UUID noticeId, boolean bookmarked) {
}
