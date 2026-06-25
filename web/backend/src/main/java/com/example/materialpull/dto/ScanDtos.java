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
