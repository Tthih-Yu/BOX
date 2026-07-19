package com.example.materialpull.enums;

public enum PlanStatus {
    DRAFT("草稿"), RELEASED("已发布"), DEMAND_GENERATED("已生成需求"), CLOSED("已关闭"), CANCELLED("已取消");
    public final String label;
    PlanStatus(String label) { this.label = label; }
}
