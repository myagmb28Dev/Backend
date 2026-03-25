package com.example.pogun.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
/**
 * API 요청/응답 데이터 전송 객체인 AdminDashboardResponse이다.
 */

@Schema(description = "관리자 대시보드 응답")
public record AdminDashboardResponse(long todayReports, long pendingReports, long hiddenCommunityPosts, long hiddenMissingPosts, long sanctionedUsers) {
}