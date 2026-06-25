package com.example.materialpull.enums;

public enum AgvJobStatus {
    CREATED("已创建"), SENT("已下发"), ACCEPTED("AGV已接收"), IN_TRANSIT("运输中"), ARRIVED("已到达"), CONFIRMED("已确认"), FAILED("失败"), CANCELLED("已取消");
    public final String label;
    AgvJobStatus(String label) { this.label = label; }
}
