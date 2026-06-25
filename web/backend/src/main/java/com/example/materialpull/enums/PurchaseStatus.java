package com.example.materialpull.enums;

public enum PurchaseStatus {
    CREATED("已生成"), SUBMITTED("已提交MPC"), CONFIRMED("已确认"), CLOSED("已关闭"), CANCELLED("已取消");
    public final String label;
    PurchaseStatus(String label) { this.label = label; }
}
