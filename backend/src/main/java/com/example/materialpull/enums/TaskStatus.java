package com.example.materialpull.enums;

public enum TaskStatus {
    CREATED("已创建"), ACCEPTED("已接单"), PICKING("拣料中"), PICKED("拣料完成"), DELIVERING("配送中"), ARRIVED("已到达"), COMPLETED("已完成"), CANCELLED("已取消"), EXCEPTION("异常");
    public final String label;
    TaskStatus(String label) { this.label = label; }
}
