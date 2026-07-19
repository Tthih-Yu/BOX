package com.example.materialpull.enums;

public enum PrintJobStatus {
    CREATED("已创建"), RENDERED("已渲染"), SENT("已发送打印机"), PRINTED("已打印"), FAILED("打印失败"), CANCELLED("已取消");
    public final String label;
    PrintJobStatus(String label) { this.label = label; }
}
