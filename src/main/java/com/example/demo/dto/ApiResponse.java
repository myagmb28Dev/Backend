package com.example.demo.dto;

import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;

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
}


