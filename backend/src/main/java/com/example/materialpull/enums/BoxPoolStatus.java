package com.example.materialpull.enums;

public enum BoxPoolStatus {
    AVAILABLE("仓库可用"), ALLOCATED("已分配"), IN_TRANSIT("配送中"), AT_LINE("已到线边"), EMPTY_RETURNING("空盒回库中"), WASHING("清洁中"), MAINTENANCE("维修中"), SCRAPPED("报废");
    public final String label;
    BoxPoolStatus(String label) { this.label = label; }
}
