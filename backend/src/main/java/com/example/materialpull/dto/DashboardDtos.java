package com.example.materialpull.dto;

import java.math.BigDecimal;
import java.util.*;

public class DashboardDtos {
    public static class Summary {
        public long materials;
        public long stationMaterials;
        public long boxes;
        public long labels;
        public long tasksCreated;
        public long tasksProcessing;
        public long tasksException;
        public long boxesAbnormal;
        public BigDecimal lowStockMaterials = BigDecimal.ZERO;
        public long urgentTasks;
        public long timeoutTasks;
        public long shortageItems;
    }
    public static class ChartItem {
        public String name;
        public Object value;
        public ChartItem(String name, Object value){this.name=name;this.value=value;}
    }
    public static class Dashboard {
        public Summary summary;
        public List<ChartItem> taskStatus;
        public List<ChartItem> boxStatus;
        public List<?> latestTasks;
        public List<?> latestScans;
        public List<?> warnings;
        public List<?> normalTasks;
        public List<?> timeoutTasks;
        public List<?> urgentTasks;
        public List<?> shortageItems;
    }
}
