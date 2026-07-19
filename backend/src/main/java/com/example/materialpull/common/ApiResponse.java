package com.example.materialpull.common;

import java.time.LocalDateTime;

public class ApiResponse<T> {
    public boolean success;
    public String code;
    public String message;
    public T data;
    public String requestId;
    public LocalDateTime timestamp = LocalDateTime.now();

    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.success = true;
        r.code = ErrorCode.OK.code;
        r.message = "OK";
        r.data = data;
        r.requestId = RequestContext.getTraceId();
        return r;
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        ApiResponse<T> r = ok(data);
        r.message = message;
        return r;
    }

    public static <T> ApiResponse<T> fail(String message) {
        return fail(ErrorCode.SYSTEM_ERROR, message);
    }

    public static <T> ApiResponse<T> fail(ErrorCode code, String message) {
        ApiResponse<T> r = new ApiResponse<>();
        r.success = false;
        r.code = code == null ? ErrorCode.SYSTEM_ERROR.code : code.code;
        r.message = message == null || message.isBlank() ? (code == null ? ErrorCode.SYSTEM_ERROR.message : code.message) : message;
        r.requestId = RequestContext.getTraceId();
        return r;
    }
}
