package com.example.materialpull.common;

public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;

    public BusinessException(String message) {
        this(ErrorCode.PARAM_ERROR, message);
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode == null ? ErrorCode.PARAM_ERROR : errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
