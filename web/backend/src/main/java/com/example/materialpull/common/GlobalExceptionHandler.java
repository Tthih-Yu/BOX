package com.example.materialpull.common;

import jakarta.persistence.OptimisticLockException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> business(BusinessException e) {
        HttpStatus status = toHttpStatus(e.getErrorCode());
        log.warn("business error http={} code={} traceId={} msg={}", status.value(), e.getErrorCode().code, RequestContext.getTraceId(), e.getMessage());
        return ResponseEntity.status(status).body(ApiResponse.fail(e.getErrorCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> valid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(x -> x.getField() + ": " + x.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ApiResponse.fail(ErrorCode.PARAM_ERROR, msg);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> constraint(ConstraintViolationException e) {
        return ApiResponse.fail(ErrorCode.PARAM_ERROR, e.getMessage());
    }

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, OptimisticLockException.class, ConcurrencyFailureException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> concurrency(Exception e) {
        log.warn("concurrency conflict traceId={} msg={}", RequestContext.getTraceId(), e.getMessage());
        return ApiResponse.fail(ErrorCode.STATE_CONFLICT, "数据已被其他人修改，请刷新后重试");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> dataConflict(DataIntegrityViolationException e) {
        log.warn("data integrity violation traceId={} msg={}", RequestContext.getTraceId(), e.getMostSpecificCause().getMessage());
        return ApiResponse.fail(ErrorCode.DATA_DIRTY, "数据唯一性或完整性校验失败，请检查编码是否重复");
    }

    @ExceptionHandler({IllegalArgumentException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> illegal(IllegalArgumentException e) {
        log.warn("illegal request traceId={} msg={}", RequestContext.getTraceId(), e.getMessage());
        return ApiResponse.fail(ErrorCode.PARAM_ERROR, e.getMessage());
    }

    @ExceptionHandler({IllegalStateException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> illegalState(IllegalStateException e) {
        log.warn("state conflict traceId={} msg={}", RequestContext.getTraceId(), e.getMessage());
        return ApiResponse.fail(ErrorCode.STATE_CONFLICT, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> unknown(Exception e) {
        log.error("system error traceId={}", RequestContext.getTraceId(), e);
        return ApiResponse.fail(ErrorCode.SYSTEM_ERROR, "系统异常，已记录追踪号：" + RequestContext.getTraceId());
    }

    private HttpStatus toHttpStatus(ErrorCode code) {
        if (code == null) return HttpStatus.BAD_REQUEST;
        return switch (code) {
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case DUPLICATE_REQUEST, STATE_CONFLICT, DATA_DIRTY -> HttpStatus.CONFLICT;
            case INVENTORY_SHORTAGE -> HttpStatus.UNPROCESSABLE_ENTITY;
            case LOCK_TIMEOUT, CIRCUIT_OPEN -> HttpStatus.TOO_MANY_REQUESTS;
            case SYSTEM_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
            default -> HttpStatus.BAD_REQUEST;
        };
    }
}
