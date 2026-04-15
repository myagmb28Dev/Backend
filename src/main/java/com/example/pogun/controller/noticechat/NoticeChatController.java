package com.example.pogun.controller.noticechat;

import com.example.pogun.dto.common.ApiResponse;
import com.example.pogun.dto.noticechat.NoticeChatMessagePageResponse;
import com.example.pogun.dto.noticechat.NoticeChatMessageResponse;
import com.example.pogun.dto.noticechat.NoticeChatRoomCreateResult;
import com.example.pogun.dto.noticechat.NoticeChatRoomResponse;
import com.example.pogun.dto.noticechat.NoticeChatRoomSettingsRequest;
import com.example.pogun.service.noticechat.NoticeChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
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
    @Operation(summary = "공고 채팅방 생성 또는 조회", description = "실종 공고 작성자와 1:1 채팅방을 생성하거나 기존 채팅방을 조회합니다.")
    public ResponseEntity<ApiResponse<NoticeChatRoomResponse>> createOrGetRoom(@PathVariable String noticeId) {
        NoticeChatRoomCreateResult result = noticeChatService.createOrGetRoom(noticeId);
        // 같은 엔드포인트라도 새 방이면 생성, 기존 방이면 재사용이라는 의미를 상태코드로 구분한다.
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

    @GetMapping("/rooms/search")
    public ResponseEntity<ApiResponse<List<NoticeChatRoomResponse>>> searchRooms(@RequestParam(value = "keyword", required = false) String keyword) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "채팅방 검색 성공", noticeChatService.searchRooms(keyword)));
    }

    @GetMapping("/rooms/{roomId}")
    public ResponseEntity<ApiResponse<NoticeChatRoomResponse>> getRoom(@PathVariable String roomId) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "채팅방 상세 조회 성공", noticeChatService.getRoom(roomId)));
    }

    @GetMapping("/rooms/{roomId}/messages")
    @Operation(summary = "채팅 메시지 목록 조회", description = "특정 채팅방의 메시지 내역을 조회합니다.")
    public ResponseEntity<ApiResponse<NoticeChatMessagePageResponse>> getMessages(
            @PathVariable String roomId,
            @RequestParam(value = "beforeSequence", required = false) Long beforeSequence,
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        NoticeChatMessagePageResponse data = noticeChatService.getMessages(roomId, beforeSequence, limit);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "채팅 메시지 조회 성공", data));
    }

    @PostMapping("/rooms/{roomId}/read")
    public ResponseEntity<ApiResponse<NoticeChatRoomResponse>> markRead(@PathVariable String roomId) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "채팅방 읽음 처리 성공", noticeChatService.markRoomAsRead(roomId)));
    }

    @PatchMapping("/rooms/{roomId}/settings")
    public ResponseEntity<ApiResponse<NoticeChatRoomResponse>> updateSettings(@PathVariable String roomId, @RequestBody NoticeChatRoomSettingsRequest request) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "채팅방 설정 변경 성공", noticeChatService.updateSettings(roomId, request)));
    }

    @PostMapping("/rooms/{roomId}/leave")
    public ResponseEntity<ApiResponse<NoticeChatRoomResponse>> leaveRoom(@PathVariable String roomId) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "채팅방 나가기 성공", noticeChatService.leaveRoom(roomId)));
    }

    @GetMapping("/rooms/{roomId}/messages/search")
    public ResponseEntity<ApiResponse<List<NoticeChatMessageResponse>>> searchMessages(@PathVariable String roomId, @RequestParam(value = "keyword", required = false) String keyword) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "채팅 메시지 검색 성공", noticeChatService.searchMessages(roomId, keyword)));
    }

    @PostMapping(value = "/rooms/{roomId}/messages/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<NoticeChatMessageResponse>> sendImages(
            @PathVariable String roomId,
            @RequestPart("images") List<MultipartFile> images,
            @RequestParam(value = "replyToMessageId", required = false) String replyToMessageId,
            @RequestParam(value = "message", required = false) String message
    ) {
        NoticeChatMessageResponse data = noticeChatService.sendImages(roomId, replyToMessageId, message, images);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(HttpStatus.CREATED, "이미지 메시지 전송 성공", data));
    }
}
