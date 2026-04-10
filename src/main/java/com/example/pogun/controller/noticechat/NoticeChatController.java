package com.example.pogun.controller.noticechat;

import com.example.pogun.dto.common.ApiResponse;
import com.example.pogun.dto.noticechat.NoticeChatMessageResponse;
import com.example.pogun.dto.noticechat.NoticeChatRoomCreateResult;
import com.example.pogun.dto.noticechat.NoticeChatRoomResponse;
import com.example.pogun.service.noticechat.NoticeChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
/**
 * HTTP/WebSocket 진입점을 담당하는 NoticeChatController이다.
 */

@RestController
@RequestMapping("/api/chat")
@Tag(name = "Notice Chat", description = "실종 공고 기반 1:1 메시지 API")
@RequiredArgsConstructor
public class NoticeChatController {
    private final NoticeChatService noticeChatService;

    @PostMapping("/rooms/notice/{noticeId}")
    @Operation(summary = "공고 기반 채팅방 생성 또는 조회", description = "특정 공고를 본 사용자가 작성자에게 1:1 문의 채팅방을 생성하거나 기존 채팅방을 재사용합니다.")
    public ResponseEntity<ApiResponse<NoticeChatRoomResponse>> createOrGetRoom(@PathVariable String noticeId) {
        NoticeChatRoomCreateResult result = noticeChatService.createOrGetRoom(noticeId);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        String message = result.created() ? "채팅방 생성 성공" : "채팅방 조회 성공";
        return ResponseEntity.status(status)
                .body(ApiResponse.success(status, message, result.room()));
    }

    @GetMapping("/rooms")
    @Operation(summary = "내 채팅방 목록 조회", description = "내가 참여 중인 공고 기반 채팅방 목록을 조회합니다.")
    public ResponseEntity<ApiResponse<List<NoticeChatRoomResponse>>> getRooms() {
        List<NoticeChatRoomResponse> data = noticeChatService.getRooms();
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "채팅방 목록 조회 성공", data));
    }

    @GetMapping("/rooms/{roomId}")
    @Operation(summary = "채팅방 상세 조회", description = "특정 공고 기반 채팅방의 상세 정보를 조회합니다.")
    public ResponseEntity<ApiResponse<NoticeChatRoomResponse>> getRoom(@PathVariable String roomId) {
        NoticeChatRoomResponse data = noticeChatService.getRoom(roomId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "채팅방 상세 조회 성공", data));
    }

    @GetMapping("/rooms/{roomId}/messages")
    @Operation(summary = "채팅 메시지 목록 조회", description = "특정 채팅방의 메시지 내역을 조회합니다.")
    public ResponseEntity<ApiResponse<List<NoticeChatMessageResponse>>> getMessages(@PathVariable String roomId) {
        // 메시지 조회 시 서비스에서 접근 권한 검증과 읽음 처리까지 함께 수행한다.
        List<NoticeChatMessageResponse> data = noticeChatService.getMessages(roomId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "채팅 메시지 조회 성공", data));
    }

    @PostMapping(value = "/rooms/{roomId}/messages/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "이미지 메시지 전송", description = "특정 채팅방에 여러 장의 이미지를 WEBP로 변환해 전송합니다.")
    public ResponseEntity<ApiResponse<NoticeChatMessageResponse>> sendImages(
            @PathVariable String roomId,
            @RequestPart("images") List<MultipartFile> images,
            @RequestParam(value = "replyToMessageId", required = false) String replyToMessageId
    ) {
        NoticeChatMessageResponse data = noticeChatService.sendImages(roomId, replyToMessageId, images);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED, "이미지 메시지 전송 성공", data));
    }
}
