package com.example.pogun.dto.common;

import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
/**
 * API 요청/응답 데이터 전송 객체인 ApiResponse이다.
 */

public record ApiResponse<T>(
        boolean ok,
        int status,
        String message,
        T data,
        ApiError error,
        ApiMeta meta
) {

    public static <T> ApiResponse<T> success(HttpStatus status, String message, T data) {
        return new ApiResponse<>(
                true,
                status.value(),
                message,
                data,
                null,
                ApiMeta.now()
        );
    }

    public static ApiResponse<Void> fail(HttpStatus status, String code, String message, Object detail) {
        return new ApiResponse<>(
                false,
                status.value(),
                message,
                null,
                new ApiError(code, message, detail),
                ApiMeta.now()
        );
    }

    public record ApiMeta(String requestId, Instant timestamp) {
        public static ApiMeta now() {
            return new ApiMeta("req_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12), Instant.now());
        }
    }

    public record ApiError(String code, String message, Object detail) {
    }

    /**
     * API 응답 형식과 함께 내려갈 상태/코드 정보를 담는 공통 예외이다.
     */
    public static class ApiException extends RuntimeException {

        private final HttpStatus status;
        private final String code;
        private final Object detail;

        public ApiException(HttpStatus status, String code, String message, Object detail) {
            super(message);
            this.status = status;
            this.code = code;
            this.detail = detail;
        }

        public HttpStatus getStatus() {
            return status;
        }

        public String getCode() {
            return code;
        }

        public Object getDetail() {
            return detail;
        }

        public static ApiException badRequest(String code, String message) {
            return new ApiException(HttpStatus.BAD_REQUEST, code, message, null);
        }

        public static ApiException badRequest(String code, String message, Object detail) {
            return new ApiException(HttpStatus.BAD_REQUEST, code, message, detail);
        }

        public static ApiException unauthorized(String code, String message) {
            return new ApiException(HttpStatus.UNAUTHORIZED, code, message, null);
        }

        public static ApiException forbidden(String code, String message) {
            return new ApiException(HttpStatus.FORBIDDEN, code, message, null);
        }

        public static ApiException notFound(String code, String message) {
            return new ApiException(HttpStatus.NOT_FOUND, code, message, null);
        }

        public static ApiException conflict(String code, String message) {
            return new ApiException(HttpStatus.CONFLICT, code, message, null);
        }

        public static ApiException internal(String code, String message) {
            return new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, code, message, null);
        }
    }
}


