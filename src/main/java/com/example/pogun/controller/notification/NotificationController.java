package com.example.pogun.controller.notification;

import com.example.pogun.dto.common.ApiResponse;
import com.example.pogun.dto.notification.NotificationDeviceDeleteResponse;
import com.example.pogun.dto.notification.NotificationFcmTokenRequest;
import com.example.pogun.dto.notification.NotificationFcmTokenResponse;
import com.example.pogun.dto.notification.NotificationDeviceResponse;
import com.example.pogun.dto.notification.NotificationListResponse;
import com.example.pogun.dto.notification.NotificationReadAllResponse;
import com.example.pogun.dto.notification.NotificationResponse;
import com.example.pogun.dto.notification.NotificationSendRequest;
import com.example.pogun.dto.notification.NotificationSettingResponse;
import com.example.pogun.dto.notification.NotificationSettingsUpdateRequest;
import com.example.pogun.dto.notification.NotificationUnreadCountResponse;
import com.example.pogun.service.notification.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;
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
    public ResponseEntity<ApiResponse<NotificationListResponse>> list(
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size,
            @RequestParam(required = false, defaultValue = "false") Boolean unreadOnly,
            @RequestParam(required = false) String type
    ) {
        NotificationListResponse data = notificationService.getNotifications(page, size, unreadOnly, type);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "알림 목록 조회 성공", data));
    }

    @GetMapping("/{notificationId}")
    @Operation(summary = "개별 알림 조회", description = "현재 로그인 사용자 소유의 알림 1건을 조회합니다.")
    public ResponseEntity<ApiResponse<NotificationResponse>> getNotification(@PathVariable String notificationId) {
        NotificationResponse data = notificationService.getNotification(notificationId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "개별 알림 조회 성공", data));
    }

    @PostMapping("/send")
    @Operation(summary = "사용자 알림 발송", description = "현재 로그인 사용자가 전체 또는 특정 사용자에게 알림을 발송합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> sendNotification(
            @Valid @RequestBody NotificationSendRequest request
    ) {
        Map<String, Object> data = notificationService.sendNotification(request);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "사용자 알림 발송 성공", data));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "읽지 않은 알림 수 조회", description = "사용자의 읽지 않은 알림 수를 조회합니다.")
    public ResponseEntity<ApiResponse<NotificationUnreadCountResponse>> unreadCount() {
        NotificationUnreadCountResponse data = notificationService.getUnreadCount();
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "읽지 않은 알림 수 조회 성공", data));
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

    @GetMapping("/settings")
    @Operation(summary = "알림 설정 조회", description = "알림 타입별 수신 설정을 조회합니다.")
    public ResponseEntity<ApiResponse<List<NotificationSettingResponse>>> settings() {
        List<NotificationSettingResponse> data = notificationService.getSettings();
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "알림 설정 조회 성공", data));
    }

    @PatchMapping("/settings")
    @Operation(summary = "알림 설정 변경", description = "알림 타입별 수신 설정을 변경합니다.")
    public ResponseEntity<ApiResponse<List<NotificationSettingResponse>>> updateSettings(@Valid @RequestBody NotificationSettingsUpdateRequest request) {
        List<NotificationSettingResponse> data = notificationService.updateSettings(request);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "알림 설정 변경 성공", data));
    }

    @PostMapping("/fcm-token")
    @Operation(summary = "FCM 토큰 갱신", description = "사용자의 FCM 토큰을 갱신합니다.")
    public ResponseEntity<ApiResponse<NotificationFcmTokenResponse>> upsertFcmToken(@Valid @RequestBody NotificationFcmTokenRequest request) {
        // 같은 사용자의 여러 디바이스를 구분할 수 있게 token 외에 platform/deviceId도 함께 갱신한다.
        NotificationFcmTokenResponse data = notificationService.upsertFcmToken(request);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "FCM 토큰 갱신 성공", data));
    }

    @GetMapping("/devices")
    @Operation(summary = "알림 기기 목록 조회", description = "현재 로그인 사용자의 FCM 기기 토큰 목록을 조회합니다.")
    public ResponseEntity<ApiResponse<List<NotificationDeviceResponse>>> devices() {
        List<NotificationDeviceResponse> data = notificationService.getDevices();
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "알림 기기 목록 조회 성공", data));
    }

    @PatchMapping("/devices/{tokenId}/mute")
    @Operation(summary = "기기 알림 끄기", description = "특정 기기의 알림 수신을 끕니다.")
    public ResponseEntity<ApiResponse<NotificationDeviceResponse>> muteDevice(@PathVariable UUID tokenId) {
        NotificationDeviceResponse data = notificationService.setDeviceActive(tokenId, false);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "기기 알림 끄기 성공", data));
    }

    @PatchMapping("/devices/{tokenId}/unmute")
    @Operation(summary = "기기 알림 켜기", description = "특정 기기의 알림 수신을 켭니다.")
    public ResponseEntity<ApiResponse<NotificationDeviceResponse>> unmuteDevice(@PathVariable UUID tokenId) {
        NotificationDeviceResponse data = notificationService.setDeviceActive(tokenId, true);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "기기 알림 켜기 성공", data));
    }

    @DeleteMapping("/devices/{tokenId}")
    @Operation(summary = "기기 FCM 토큰 삭제", description = "현재 로그인 사용자의 특정 기기 FCM 토큰을 삭제합니다. 같은 platform/deviceId의 과거 토큰도 함께 삭제합니다.")
    public ResponseEntity<ApiResponse<NotificationDeviceDeleteResponse>> deleteDevice(@PathVariable UUID tokenId) {
        NotificationDeviceDeleteResponse data = notificationService.deleteDevice(tokenId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "기기 FCM 토큰 삭제 성공", data));
    }
}
