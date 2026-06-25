package com.example.materialpull.enums;

public enum PriorityLevel {
    LOW("低"), NORMAL("普通"), HIGH("高"), URGENT("紧急");
    public final String label;
    PriorityLevel(String label) { this.label = label; }
}
