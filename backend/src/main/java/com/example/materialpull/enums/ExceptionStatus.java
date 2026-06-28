package com.example.materialpull.enums;

public enum ExceptionStatus {
    OPEN("待处理"), PROCESSING("处理中"), CLOSED("已关闭");
    public final String label;
    ExceptionStatus(String label) { this.label = label; }
}
