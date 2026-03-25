package com.example.pogun.dto.noticechat;
/**
 * API 요청/응답 데이터 전송 객체인 NoticeChatRoomCreateResult이다.
 */

public record NoticeChatRoomCreateResult(
        boolean created,
        NoticeChatRoomResponse room
) {
}
