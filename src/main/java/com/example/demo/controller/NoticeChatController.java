package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.service.NoticeChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@Tag(name = "Notice Chat", description = "실종 공고 기반 1:1 메시지 API")
@RequiredArgsConstructor
public class NoticeChatController {
    private final NoticeChatService noticeChatService;

    @PostMapping("/rooms/notice/{noticeId}")
    @Operation(summary = "공고 채팅방 생성 또는 조회", description = "실종 공고 작성자와 1:1 채팅방을 생성하거나 기존 채팅방을 조회합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createOrGetRoom(@PathVariable String noticeId) {
        Map<String, Object> data = noticeChatService.createOrGetRoom(noticeId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED, "채팅방 조회 성공", data));
    }

    @GetMapping("/rooms")
    @Operation(summary = "내 채팅방 목록 조회", description = "내가 참여 중인 공고 기반 채팅방 목록을 조회합니다.")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getRooms() {
        List<Map<String, Object>> data = noticeChatService.getRooms();
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "채팅방 목록 조회 성공", data));
    }

    @GetMapping("/rooms/{roomId}/messages")
    @Operation(summary = "채팅 메시지 목록 조회", description = "특정 채팅방의 메시지 내역을 조회합니다.")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getMessages(@PathVariable String roomId) {
        List<Map<String, Object>> data = noticeChatService.getMessages(roomId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "채팅 메시지 조회 성공", data));
    }
}
