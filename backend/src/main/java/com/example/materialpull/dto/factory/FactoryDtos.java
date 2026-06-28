package com.example.materialpull.dto.factory;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

public class FactoryDtos {
    public static class ReceiveRequest {
        public String taskNo;
        public String scanCode;
        public String operator;
        public String deviceNo;
        public String emptyContainerNo;
        public String remark;
        public String requestId;
    }

    public static class ExceptionScanRequest {
        public String taskNo;
        public String scanCode;
        public String exceptionType = "SITE_EXCEPTION";
        public String level = "WARN";
        public String reason;
        public String operator;
        public String deviceNo;
        public String requestId;
    }

    public static class PrintRequest {
        public String taskNo;
        public String labelCode;
        public String printType = "OUTBOUND_LABEL";
        public String printerName;
        public String operator;
    }

    public static class PrintCallbackRequest {
        public String printJobNo;
        public String status;
        public String externalJobNo;
        public String message;
        public String rawPayload;
    }

    public static class AgvDispatchRequest {
        public String taskNo;
        public String containerNo;
        public String fromLocation;
        public String toLocation;
        public String jobType = "DELIVER_FULL_BOX";
        public String operator;
    }

    public static class AgvCallbackRequest {
        public String agvJobNo;
        public String status;
        public String externalJobNo;
        public String message;
    }

    public static class PlanGenerateResult {
        public Object plan;
        public List<?> demands = new ArrayList<>();
        public List<?> tasks = new ArrayList<>();
        public List<?> purchases = new ArrayList<>();
        public List<?> adjustments = new ArrayList<>();
        public List<?> forecasts = new ArrayList<>();
    }

    public static class IntegrationPayload {
        public String systemCode;
        public String externalKey;
        public String productCode;
        public String productName;
        public String processCode;
        public String processName;
        public String bundleCode;
        public String lineCode;
        public String stationCode;
        public String stationName;
        public String rackCode;
        public String shelfCode;
        public String materialCode;
        public String materialName;
        public String warehouseMaterialCode;
        public String warehouseCode;
        public BigDecimal usageQty;
        public BigDecimal stockQty;
        public BigDecimal safetyStock;
        public BigDecimal minStock;
        public BigDecimal maxStock;
        public BigDecimal planQty;
        public BigDecimal boxQty;
        public BigDecimal singleBoxQty;
        public BigDecimal mpcThresholdQty;
        public Integer productionCycleMinutes;
        public Integer deliveryCycleMinutes;
        public LocalDateTime dueAt;
        public String rawPayload;
    }

    public static class InventoryAdjustmentRow {
        public String planNo;
        public String demandNo;
        public String lineCode;
        public String stationCode;
        public String stationName;
        public String bundleCode;
        public String materialCode;
        public String materialName;
        public String warehouseMaterialCode;
        public BigDecimal demandQty = BigDecimal.ZERO;
        public BigDecimal singleBoxQty = BigDecimal.ZERO;
        public Integer taskBoxCount = 0;
        public BigDecimal currentStock = BigDecimal.ZERO;
        public BigDecimal safetyStock = BigDecimal.ZERO;
        public BigDecimal inventoryDifferenceQty = BigDecimal.ZERO;
        public BigDecimal adjustmentQty = BigDecimal.ZERO;
        public String printTarget;
        public String status;
    }

    public static class MaterialForecastRow {
        public String planNo;
        public String demandNo;
        public String materialCode;
        public String materialName;
        public String warehouseMaterialCode;
        public BigDecimal forecastQty = BigDecimal.ZERO;
        public BigDecimal currentStock = BigDecimal.ZERO;
        public BigDecimal thresholdQty = BigDecimal.ZERO;
        public BigDecimal purchaseSuggestionQty = BigDecimal.ZERO;
        public String source;
        public String status;
    }

    public static class MetaOption {
        public String label;
        public String value;
        public MetaOption() {}
        public MetaOption(String label, String value) { this.label = label; this.value = value; }
    }

    public static class BusinessMeta {
        public List<MetaOption> userRoles = new ArrayList<>();
        public List<MetaOption> taskStatuses = new ArrayList<>();
        public List<MetaOption> boxPoolStatuses = new ArrayList<>();
        public List<MetaOption> planStatuses = new ArrayList<>();
        public List<MetaOption> demandStatuses = new ArrayList<>();
        public List<MetaOption> purchaseStatuses = new ArrayList<>();
        public List<MetaOption> labelUsageTypes = new ArrayList<>();
        public List<MetaOption> deliveryModes = new ArrayList<>();
        public List<MetaOption> exceptionTypes = new ArrayList<>();
        public List<MetaOption> boxSides = new ArrayList<>();
        public List<MetaOption> agvCallbackStatuses = new ArrayList<>();
    }
}
