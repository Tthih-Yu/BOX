package com.example.materialpull.enums;

public enum DemandStatus {
    OPEN("待处理"), PULL_TASK_CREATED("已生成拉动任务"), PURCHASE_REQUIRED("需采购"), CLOSED("已关闭"), CANCELLED("已取消");
    public final String label;
    DemandStatus(String label) { this.label = label; }
}
