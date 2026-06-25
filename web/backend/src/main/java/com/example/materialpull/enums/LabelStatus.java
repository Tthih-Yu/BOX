package com.example.materialpull.enums;

public enum LabelStatus {
    UNUSED("未使用"), BOUND("已绑定"), USING("使用中"), VOIDED("已作废"), REPRINTED("已重打");
    public final String label;
    LabelStatus(String label) { this.label = label; }
}
