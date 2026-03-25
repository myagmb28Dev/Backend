package com.example.pogun.dto.notification;

import io.swagger.v3.oas.annotations.media.Schema;
/**
 * API 요청/응답 데이터 전송 객체인 NotificationReadAllResponse이다.
 */

@Schema(description = "전체 알림 읽음 처리 응답")
public record NotificationReadAllResponse(long updatedCount) {
}
