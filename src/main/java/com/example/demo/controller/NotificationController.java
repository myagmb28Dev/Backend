package com.example.demo.controller;

import java.util.List;
import java.util.Map;

import com.example.demo.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications", description = "알림 관련 API")
public class NotificationController {

    @GetMapping
    @Operation(summary = "알림 목록 조회", description = "사용자에게 전달된 알림 목록을 조회합니다.")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> list() {
        List<Map<String, Object>> data = List.of(
                Map.of("id", "ntf-1", "title", "새 공고 등록", "isRead", false),
                Map.of("id", "ntf-2", "title", "내 공고에 댓글", "isRead", true)
        );
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "알림 목록 조회 성공", data));
    }

    @PatchMapping("/{notificationId}/read")
    @Operation(summary = "알림 읽음 처리", description = "지정한 알림을 읽음 처리합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> markRead(@PathVariable String notificationId) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "알림 읽음 처리 성공", Map.of("id", notificationId, "isRead", true)));
    }

    @PatchMapping("/read-all")
    @Operation(summary = "전체 알림 읽음 처리", description = "모든 알림을 읽음 처리합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> markReadAll() {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "알림 전체 읽음 처리 성공", Map.of("updatedCount", 12)));
    }

    @PostMapping("/fcm-token")
    @Operation(summary = "FCM 토큰 갱신", description = "사용자의 FCM 토큰을 갱신합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> upsertFcmToken(@RequestBody Map<String, String> request) {
        return ResponseEntity.ok(ApiResponse.success(
                HttpStatus.OK,
                "FCM 토큰 갱신 성공",
                Map.of("token", request.getOrDefault("token", "sample-fcm-token"), "platform", request.getOrDefault("platform", "ANDROID"))
        ));
    }
}


