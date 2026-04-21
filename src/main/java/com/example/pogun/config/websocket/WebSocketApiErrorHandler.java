package com.example.pogun.config;

import com.example.pogun.dto.common.ApiResponse;
import com.example.pogun.dto.common.ApiResponse.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

import java.nio.charset.StandardCharsets;

/**
 * 애플리케이션 설정을 담당하는 WebSocketApiErrorHandler이다.
 */

@Component
@RequiredArgsConstructor
public class WebSocketApiErrorHandler extends StompSubProtocolErrorHandler {
    private final ObjectMapper objectMapper;

    @Override
    public Message<byte[]> handleClientMessageProcessingError(Message<byte[]> clientMessage, Throwable ex) {
        Throwable cause = ex instanceof RuntimeException runtime && runtime.getCause() != null
                ? runtime.getCause()
                : ex;
        ApiResponse<Void> payload = toApiResponse(cause);
        byte[] body = serialize(payload);

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.ERROR);
        accessor.setLeaveMutable(true);
        accessor.setContentType(MediaType.APPLICATION_JSON);
        accessor.setMessage(payload.message());

        return MessageBuilder.createMessage(body, accessor.getMessageHeaders());
    }

    private ApiResponse<Void> toApiResponse(Throwable throwable) {
        if (throwable instanceof ApiException apiException) {
            return ApiResponse.fail(apiException.getStatus(), apiException.getCode(), apiException.getMessage(), apiException.getDetail());
        }
        if (throwable instanceof IllegalArgumentException illegalArgumentException) {
            return ApiResponse.fail(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", illegalArgumentException.getMessage(), null);
        }
        return ApiResponse.fail(HttpStatus.INTERNAL_SERVER_ERROR, "WEBSOCKET_INTERNAL_ERROR", "웹소켓 처리 중 오류가 발생했습니다.", null);
    }

    private byte[] serialize(ApiResponse<Void> payload) {
        try {
            return objectMapper.writeValueAsBytes(payload);
        } catch (JsonProcessingException e) {
            return """
                    {"ok":false,"status":500,"message":"웹소켓 처리 중 오류가 발생했습니다.","data":null,"error":{"code":"WEBSOCKET_INTERNAL_ERROR","message":"웹소켓 처리 중 오류가 발생했습니다.","detail":null},"meta":null}
                    """.getBytes(StandardCharsets.UTF_8);
        }
    }
}
