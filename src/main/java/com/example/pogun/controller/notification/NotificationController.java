package com.example.pogun.controller.notification;

import com.example.pogun.dto.common.ApiResponse;
import com.example.pogun.dto.notification.NotificationFcmTokenRequest;
import com.example.pogun.dto.notification.NotificationFcmTokenResponse;
import com.example.pogun.dto.notification.NotificationReadAllResponse;
import com.example.pogun.dto.notification.NotificationResponse;
import com.example.pogun.service.notification.NotificationService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
/**
 * HTTP/WebSocket 진입점을 담당하는 NotificationController이다.
 */

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications", description = "알림 관련 API")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @Operation(summary = "알림 목록 조회", description = "사용자에게 전달된 알림 목록을 조회합니다.")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> list() {
        List<NotificationResponse> data = notificationService.getNotifications();
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "알림 목록 조회 성공", data));
    }

    @PatchMapping("/{notificationId}/read")
    @Operation(summary = "알림 읽음 처리", description = "지정한 알림을 읽음 처리합니다.")
    public ResponseEntity<ApiResponse<NotificationResponse>> markRead(@PathVariable String notificationId) {
        NotificationResponse data = notificationService.markNotificationAsRead(notificationId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "알림 읽음 처리 성공", data));
    }

    @PatchMapping("/read-all")
    @Operation(summary = "전체 알림 읽음 처리", description = "모든 알림을 읽음 처리합니다.")
    public ResponseEntity<ApiResponse<NotificationReadAllResponse>> markReadAll() {
        NotificationReadAllResponse data = notificationService.markAllNotificationsAsRead();
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "알림 전체 읽음 처리 성공", data));
    }

    @PostMapping("/fcm-token")
    @Operation(summary = "FCM 토큰 갱신", description = "사용자의 FCM 토큰을 갱신합니다.")
    public ResponseEntity<ApiResponse<NotificationFcmTokenResponse>> upsertFcmToken(@Valid @RequestBody NotificationFcmTokenRequest request) {
        // 같은 사용자의 여러 디바이스를 구분할 수 있게 token 외에 platform/deviceId도 함께 갱신한다.
        NotificationFcmTokenResponse data = notificationService.upsertFcmToken(request.toRequestMap());
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "FCM 토큰 갱신 성공", data));
    }
}
