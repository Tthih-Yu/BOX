package com.example.materialpull.enums;

public enum SimpleBomBatchStatus {
    RUNNING("导入中"),
    READY("待激活"),
    ACTIVE("当前有效"),
    ARCHIVED("已归档"),
    FAILED("失败");
    public final String label;
    SimpleBomBatchStatus(String label) { this.label = label; }
}
