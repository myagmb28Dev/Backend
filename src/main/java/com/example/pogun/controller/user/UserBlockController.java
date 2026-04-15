package com.example.pogun.controller.user;

import com.example.pogun.dto.common.ApiResponse;
import com.example.pogun.service.user.UserBlockService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserBlockController {
    private final UserBlockService userBlockService;

    @PostMapping("/{userId}/block")
    public ResponseEntity<ApiResponse<Void>> block(@PathVariable String userId) {
        userBlockService.block(userId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "사용자 차단 완료", null));
    }

    @DeleteMapping("/{userId}/block")
    public ResponseEntity<ApiResponse<Void>> unblock(@PathVariable String userId) {
        userBlockService.unblock(userId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "사용자 차단 해제 완료", null));
    }
}
