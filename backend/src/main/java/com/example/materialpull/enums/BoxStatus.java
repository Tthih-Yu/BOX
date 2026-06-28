package com.example.materialpull.enums;

public enum BoxStatus {
    EMPTY_WAITING_PULL("空盒待拉动"), IN_USE("正在使用"), FULL_STANDBY("满盒备用"), REPLENISHING("补料中"), IN_TRANSIT("配送中"), ABNORMAL("异常锁定"), SCRAPPED("已报废");
    public final String label;
    BoxStatus(String label) { this.label = label; }
}
