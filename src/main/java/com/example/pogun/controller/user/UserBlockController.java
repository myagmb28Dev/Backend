package com.example.pogun.controller.user;

import com.example.pogun.dto.common.ApiResponse;
import com.example.pogun.dto.user.UserBlockResponse;
import com.example.pogun.service.user.UserBlockService;
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

@RestController
@RequestMapping("/api/users")
@Tag(name = "User Blocks", description = "사용자 차단 API")
@RequiredArgsConstructor
public class UserBlockController {
    private final UserBlockService userBlockService;

    @GetMapping("/blocks")
    @Operation(summary = "차단 사용자 목록 조회")
    public ResponseEntity<ApiResponse<List<UserBlockResponse>>> blocks() {
        List<UserBlockResponse> data = userBlockService.listBlockedUsers();
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "차단 사용자 목록 조회 성공", data));
    }

    @PostMapping("/{userId}/block")
    @Operation(summary = "사용자 차단")
    public ResponseEntity<ApiResponse<Void>> block(@PathVariable String userId) {
        userBlockService.block(userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED, "사용자 차단 성공", null));
    }

    @DeleteMapping("/{userId}/block")
    @Operation(summary = "사용자 차단 해제")
    public ResponseEntity<ApiResponse<Void>> unblock(@PathVariable String userId) {
        userBlockService.unblock(userId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "사용자 차단 해제 성공", null));
    }
}
