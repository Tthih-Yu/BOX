package com.example.materialpull.enums;

public enum UserRole {
    ADMIN("管理员"),
    PLANNER("计划员"),
    WAREHOUSE("仓库员"),
    LINE("产线员工"),
    VIEWER("只读用户"),
    SYSTEM("系统接口");

    public final String label;
    UserRole(String label) { this.label = label; }
}
