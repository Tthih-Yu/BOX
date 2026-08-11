package com.example.materialpull.enums;

public enum WeeklyPlanStatus {
    RUNNING("处理中"),
    SUCCESS("成功"),
    PARTIAL_SUCCESS("部分成功"),
    FAILED("失败");
    public final String label;
    WeeklyPlanStatus(String label) { this.label = label; }
}
