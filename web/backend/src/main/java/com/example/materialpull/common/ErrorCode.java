package com.example.materialpull.common;

public enum ErrorCode {
    OK("00000", "成功"),
    PARAM_ERROR("A0400", "参数错误"),
    UNAUTHORIZED("A0401", "未登录或登录失效"),
    FORBIDDEN("A0403", "无权限"),
    NOT_FOUND("A0404", "资源不存在"),
    DUPLICATE_REQUEST("A0409", "重复请求"),
    STATE_CONFLICT("B0409", "状态冲突"),
    DATA_DIRTY("B0410", "数据不一致"),
    INVENTORY_SHORTAGE("B0501", "库存不足"),
    LOCK_TIMEOUT("B0508", "系统繁忙，请稍后重试"),
    CIRCUIT_OPEN("B0529", "服务保护中，请稍后重试"),
    SYSTEM_ERROR("B9999", "系统异常");

    public final String code;
    public final String message;
    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
