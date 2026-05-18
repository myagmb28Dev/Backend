package com.example.pogun.controller.noticechat;

import com.example.pogun.dto.common.ApiResponse;
import com.example.pogun.dto.noticechat.NoticeChatImageOriginalResponse;
import com.example.pogun.dto.noticechat.NoticeChatMessagePageResponse;
import com.example.pogun.dto.noticechat.NoticeChatReadRequest;
import com.example.pogun.dto.noticechat.NoticeChatMessageResponse;
import com.example.pogun.dto.noticechat.NoticeChatMessageUpdateRequest;
import com.example.pogun.dto.noticechat.NoticeChatRoomSettingsRequest;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    public ResponseEntity<ApiResponse<NoticeChatRoomResponse>> createOrGetRoom(@PathVariable("noticeId") String noticeId) {
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

    @GetMapping("/rooms/search")
    @Operation(summary = "내 채팅방 검색", description = "공고 제목 또는 상대 닉네임으로 내 채팅방을 검색합니다.")
    public ResponseEntity<ApiResponse<List<NoticeChatRoomResponse>>> searchRooms(@RequestParam(value = "keyword", required = false) String keyword) {
        List<NoticeChatRoomResponse> data = noticeChatService.searchRooms(keyword);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "채팅방 검색 성공", data));
    }

    @GetMapping("/rooms/{roomId}")
    @Operation(summary = "채팅방 상세 조회", description = "특정 공고 기반 채팅방의 상세 정보를 조회합니다.")
    public ResponseEntity<ApiResponse<NoticeChatRoomResponse>> getRoom(@PathVariable("roomId") String roomId) {
        NoticeChatRoomResponse data = noticeChatService.getRoom(roomId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "채팅방 상세 조회 성공", data));
    }

    @GetMapping("/rooms/{roomId}/messages")
    @Operation(summary = "채팅 메시지 목록 조회", description = "특정 채팅방의 메시지 내역을 조회합니다.")
    public ResponseEntity<ApiResponse<NoticeChatMessagePageResponse>> getMessages(
            @PathVariable("roomId") String roomId,
            @RequestParam(value = "beforeSequence", required = false) Long beforeSequence,
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        NoticeChatMessagePageResponse data = noticeChatService.getMessages(roomId, beforeSequence, limit);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "채팅 메시지 조회 성공", data));
    }

    @PostMapping("/rooms/{roomId}/read")
    @Operation(summary = "채팅방 읽음 처리", description = "특정 채팅방의 상대 메시지를 읽음 처리합니다.")
    public ResponseEntity<ApiResponse<NoticeChatRoomResponse>> markRead(
            @PathVariable("roomId") String roomId,
            @RequestBody(required = false) NoticeChatReadRequest request
    ) {
        NoticeChatRoomResponse data = noticeChatService.markRoomAsRead(roomId, request);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "채팅방 읽음 처리 성공", data));
    }

    @PatchMapping("/rooms/{roomId}/settings")
    @Operation(summary = "채팅방 개인 설정 변경", description = "채팅방 알림, 즐겨찾기, 상단 고정 여부를 변경합니다.")
    public ResponseEntity<ApiResponse<NoticeChatRoomResponse>> updateSettings(
            @PathVariable("roomId") String roomId,
            @RequestBody NoticeChatRoomSettingsRequest request
    ) {
        NoticeChatRoomResponse data = noticeChatService.updateSettings(roomId, request);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "채팅방 설정 변경 성공", data));
    }

    @PostMapping(value = "/rooms/{roomId}/settings/thumbnail", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "채팅방 개인 썸네일 이미지 변경", description = "사용자별 채팅방 썸네일 이미지를 파일 업로드로 변경합니다.")
    public ResponseEntity<ApiResponse<NoticeChatRoomResponse>> updateRoomThumbnail(
            @PathVariable("roomId") String roomId,
            @RequestPart("image") MultipartFile image
    ) {
        NoticeChatRoomResponse data = noticeChatService.updateRoomThumbnail(roomId, image);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "채팅방 썸네일 변경 성공", data));
    }

    @PostMapping("/rooms/{roomId}/leave")
    @Operation(summary = "채팅방 나가기", description = "현재 사용자 기준으로 채팅방을 목록에서 제외합니다.")
    public ResponseEntity<ApiResponse<NoticeChatRoomResponse>> leaveRoom(@PathVariable("roomId") String roomId) {
        NoticeChatRoomResponse data = noticeChatService.leaveRoom(roomId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "채팅방 나가기 성공", data));
    }

    @GetMapping("/rooms/{roomId}/messages/search")
    @Operation(summary = "채팅 메시지 검색", description = "특정 채팅방의 텍스트 메시지를 검색합니다.")
    public ResponseEntity<ApiResponse<List<NoticeChatMessageResponse>>> searchMessages(
            @PathVariable("roomId") String roomId,
            @RequestParam(value = "keyword", required = false) String keyword
    ) {
        List<NoticeChatMessageResponse> data = noticeChatService.searchMessages(roomId, keyword);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "채팅 메시지 검색 성공", data));
    }

    @PatchMapping("/rooms/{roomId}/messages/{messageId}")
    @Operation(summary = "채팅 메시지 수정", description = "본인이 보낸 텍스트 메시지를 수정합니다.")
    public ResponseEntity<ApiResponse<NoticeChatMessageResponse>> updateMessage(
            @PathVariable("roomId") String roomId,
            @PathVariable("messageId") String messageId,
            @RequestBody NoticeChatMessageUpdateRequest request
    ) {
        NoticeChatMessageResponse data = noticeChatService.updateMessage(roomId, messageId, request);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "채팅 메시지 수정 성공", data));
    }

    @DeleteMapping("/rooms/{roomId}/messages/{messageId}")
    @Operation(summary = "채팅 메시지 삭제", description = "본인이 보낸 메시지를 화면과 API에서 숨김 처리합니다.")
    public ResponseEntity<ApiResponse<Void>> deleteMessage(
            @PathVariable("roomId") String roomId,
            @PathVariable("messageId") String messageId
    ) {
        noticeChatService.deleteMessage(roomId, messageId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "채팅 메시지 삭제 성공", null));
    }

    @GetMapping("/messages/images/{imageId}/original")
    @Operation(summary = "채팅 이미지 원본 다운로드 URL 조회", description = "해당 채팅방 참여자에게만 원본 이미지 URL을 반환합니다.")
    public ResponseEntity<ApiResponse<NoticeChatImageOriginalResponse>> getOriginalImage(@PathVariable("imageId") String imageId) {
        NoticeChatImageOriginalResponse data = noticeChatService.getOriginalImage(imageId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "채팅 이미지 원본 조회 성공", data));
    }

    @PostMapping(value = "/rooms/{roomId}/messages/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "미디어 메시지 전송", description = "특정 채팅방에 이미지/GIF/MP4 첨부를 전송합니다. PNG/JPEG는 WEBP 파생본으로 저장합니다.")
    public ResponseEntity<ApiResponse<NoticeChatMessageResponse>> sendImages(
            @PathVariable("roomId") String roomId,
            @RequestPart("images") List<MultipartFile> images,
            @RequestParam(value = "replyToMessageId", required = false) String replyToMessageId,
            @RequestParam(value = "message", required = false) String message
    ) {
        NoticeChatMessageResponse data = noticeChatService.sendImages(roomId, replyToMessageId, message, images);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED, "이미지 메시지 전송 성공", data));
    }
}
