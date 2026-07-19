package com.example.materialpull.dto;

import com.example.materialpull.enums.BoxStatus;
import com.example.materialpull.enums.TaskStatus;
import java.time.LocalDateTime;
import java.util.List;

public class ScanDtos {
    public static class ScanRequest {
        /** 兼容旧字段。现场可直接传扫码枪扫出的数字，例如 139315。 */
        public String labelCode;
        public String scanCode;
        /**
         * 工位地址/工位号。工位二维码内容为 "物料号,工位,使用/备用"，后端解析后填入。
         * 用于同一物料喂多个工位时，锁定该工位对应的仓库代号。
         */
        public String stationCode;
        /**
         * 用途：USE=使用(正常配送) / SPARE=备用(紧急配送)。
         * 来源于工位二维码第三段 "使用"/"备用"，由后端解析填入。
         */
        public String usageType;
        public String action = "EMPTY";
        public String operator;
        public String deviceNo;
        public String clientTime;
        /** 收货、异常、空盒回收等现场二次扫码可直接传任务号。 */
        public String taskNo;
        public String exceptionType;
        public String reason;
        public String emptyContainerNo;
        public String idempotencyKey;
        public Boolean allowRepeat = false;
    }
    public static class ScanResult {
        public boolean taskCreated;
        public String taskNo;
        public String message;
        public String scannedCode;
        public String resolvedLabelCode;
        public String labelType;
        public String codeCarrierType;
        public String primaryScanValue;
        public String barcodeValue;
        public String warehouseCode;
        public String warehouseAddress;
        public String sendStationAddress;
        public String boxSize;
        public java.math.BigDecimal requestQty;
        public String delivererEmployeeNo;
        public String kanbanCardNo;
        public String materialCode;
        public String materialName;
        public String materialImageUrl;
        public String deliveryAddress;
        public String warehouseLocation;
        public String currentBoxCode;
        public BoxStatus currentBoxStatus;
        public String standbyBoxCode;
        public BoxStatus standbyBoxStatus;
        public TaskStatus taskStatus;
        public String priority;
        public String exceptionNo;
        public String receiveStatus;
        public String agvJobNo;
        public String printJobNo;
        public boolean duplicateBlocked;
        public List<String> warnings;
        public LocalDateTime scanAt;
    }
}
