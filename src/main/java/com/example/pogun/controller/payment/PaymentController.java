package com.example.pogun.controller.payment;

import com.example.pogun.dto.common.ApiResponse;
import com.example.pogun.dto.payment.AppleAppStoreNotificationRequest;
import com.example.pogun.dto.payment.AppleAppStoreNotificationResponse;
import com.example.pogun.dto.payment.PaymentVerifyRequest;
import com.example.pogun.dto.payment.PaymentVerifyResponse;
import com.example.pogun.service.payment.AppleAppStoreNotificationService;
import com.example.pogun.service.payment.PaymentService;
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

@RestController
@RequestMapping("/api/payments")
@Tag(name = "Payments", description = "인앱 결제 검증 API")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final AppleAppStoreNotificationService appleAppStoreNotificationService;

    @PostMapping("/verify")
    @Operation(summary = "인앱 결제 검증", description = "App Store Server API 또는 Google Play Developer API로 결제를 검증하고 크레딧을 적립합니다.")
    public ResponseEntity<ApiResponse<PaymentVerifyResponse>> verify(@Valid @RequestBody PaymentVerifyRequest request) {
        PaymentVerifyResponse response = paymentService.verify(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED, "결제 검증 및 크레딧 적립이 완료되었습니다.", response));
    }

    @PostMapping("/apple/notifications")
    @Operation(summary = "Apple App Store 서버 알림 수신", description = "App Store Server Notifications V2 signedPayload를 검증하고 결제 상태를 동기화합니다.")
    public ResponseEntity<ApiResponse<AppleAppStoreNotificationResponse>> receiveAppleNotification(
            @Valid @RequestBody AppleAppStoreNotificationRequest request
    ) {
        AppleAppStoreNotificationResponse response = appleAppStoreNotificationService.handle(request);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Apple 서버 알림 처리 완료", response));
    }
}
