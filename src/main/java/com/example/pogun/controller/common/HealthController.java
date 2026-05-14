package com.example.pogun.controller.common;

import com.example.pogun.dto.common.ApiResponse;
import com.example.pogun.config.startup.StartupWarmupState;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 배포 환경의 헬스체크와 간단한 상태 확인용 엔드포인트이다.
 */
@RestController
@RequiredArgsConstructor
public class HealthController {

    private final StartupWarmupState startupWarmupState;

    @GetMapping({"/", "/health"})
    public ResponseEntity<ApiResponse<Map<String, String>>> health() {
        if (!startupWarmupState.isReady()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.success(HttpStatus.SERVICE_UNAVAILABLE, "서비스 워밍업 중입니다.", Map.of("status", "WARMING_UP")));
        }
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "서비스가 정상 동작 중입니다.", Map.of("status", "UP")));
    }

    @GetMapping("/favicon.ico")
    public ResponseEntity<Void> favicon() {
        return ResponseEntity.noContent().build();
    }
}
