package com.bipros.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        T data,
        ApiError error,
        Meta meta
) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(data, null, Meta.now());
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(null, new ApiError(code, message, null), Meta.now());
    }

    public static <T> ApiResponse<T> error(ApiError apiError) {
        return new ApiResponse<>(null, apiError, Meta.now());
    }

    public record Meta(Instant timestamp, String version) {
        public static Meta now() {
            return new Meta(Instant.now(), "0.1.0");
        }
    }
}
