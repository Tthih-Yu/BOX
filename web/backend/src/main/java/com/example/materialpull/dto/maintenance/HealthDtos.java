package com.example.materialpull.dto.maintenance;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class HealthDtos {
    public static class HealthReport {
        public String status;
        public LocalDateTime checkedAt = LocalDateTime.now();
        public List<CheckItem> items = new ArrayList<>();
        public int openAlertCount;
        public int errorCount;
        public int warningCount;
    }
    public static class CheckItem {
        public String code;
        public String level;
        public String title;
        public String message;
        public Object detail;
        public CheckItem() {}
        public CheckItem(String code, String level, String title, String message, Object detail) {
            this.code = code; this.level = level; this.title = title; this.message = message; this.detail = detail;
        }
    }
    public static class RecoveryResult {
        public int scanned;
        public int fixed;
        public int warned;
        public List<String> messages = new ArrayList<>();
    }
}
