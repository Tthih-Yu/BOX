package com.example.materialpull.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class LabelDtos {
    public static class GenerateRequest {
        public String stationCode;
        public String materialCode;
        public String templateCode = "KANBAN_SITE";
        public Integer count = 2;
        public String operator;
    }

    /** 按第一类现场看板卡标签手工建档或导入后落库。 */
    public static class SiteLabelRequest {
        public String areaCode;
        public String kanbanCardNo;
        public String barcodeValue;
        public String projectCode;
        public String routeName;
        public String deliveryAddress;
        public String materialCode;
        public String materialName;
        public String materialImageUrl;
        public String warehouseMaterialCode;
        public BigDecimal standardQty;
        public String boxSide;
        public String labelUsageType;
        public String deliveryMode;
        public String containerType;
        public String warehouseLocation;
        public String specText;
        public String unit;
        public LocalDate printDate;
        public String lineCode;
        public String stationCode;
        public String stationName;
        public String operator;
        public Boolean bindBox = true;
        public String labelType = "SITE_KANBAN_BARCODE";
        public String codeCarrierType = "BARCODE_1D";
        public String primaryScanValue;
        public String secondaryScanValue;
        public String rawPayload;
        public String templateCode = "KANBAN_SITE";
    }



    /**
     * V0.6 负责人确认后的真实工厂标签：条形码=仓库代码，标签内容为物料名称、仓库地址、发送工位地址、盒子大小、数量、送料人工号。
     * 该标签不默认绑定 A/B 双盒；扫码后可直接生成普通补货任务，避免把错误模板写死到主流程。
     */
    public static class FactoryPullLabelRequest {
        public String warehouseCode;
        public String barcodeValue;
        public String primaryScanValue;
        public String materialCode;
        public String materialName;
        public String materialImageUrl;
        public String warehouseMaterialCode;
        public String warehouseAddress;
        public String sendStationAddress;
        public String boxSize;
        public BigDecimal standardQty;
        public String unit;
        public String delivererEmployeeNo;
        public String lineCode;
        public String stationCode;
        public String stationName;
        public String operator;
        public Boolean bindBox = false;
        public String boxSide;
        public String labelUsageType;
        public String deliveryMode;
        public String containerType;
        public String templateCode = "FACTORY_PULL";
        public String codeCarrierType = "BARCODE_1D";
        public LocalDate printDate;
        public String remark;
    }

    /** 通用标签建档：用于二维码、POINT OF USE、供应商标签、临时手写码等后续扩展。 */
    public static class UniversalLabelRequest {
        public String labelType = "POINT_OF_USE_CARD";
        public String codeCarrierType = "MANUAL";
        public String templateCode = "POINT_OF_USE";
        public String labelCode;
        public String primaryScanValue;
        public String secondaryScanValue;
        public String barcodeValue;
        /** V0.6 真实标签字段：仓库代码，条形码默认扫出来就是这个值。 */
        public String warehouseCode;
        public String warehouseAddress;
        public String sendStationAddress;
        public String boxSize;
        public String delivererEmployeeNo;
        public String kanbanCardNo;
        public String rawPayload;
        public String areaCode;
        public String projectCode;
        public String routeName;
        public String deliveryAddress;
        public String businessCode;
        public String gridCode;
        public String pointOfUseAddress;
        public String routing;
        public Integer cardNo;
        public Integer cardTotal;
        public String supermarketBusiness;
        public String supermarketGrid;
        public String supermarketAddress;
        public String materialCode;
        public String materialName;
        public String materialImageUrl;
        public String warehouseMaterialCode;
        public BigDecimal standardQty;
        public String boxSide;
        public String labelUsageType;
        public String deliveryMode;
        public String containerType;
        public String warehouseLocation;
        public String specText;
        public String unit;
        public LocalDate printDate;
        public String lineCode;
        public String stationCode;
        public String stationName;
        public String operator;
        public Boolean bindBox = false;
        public String fieldSnapshotJson;
        public String remark;
    }

    public static class PrintRequest {
        public String operator;
        public String printerName;
        public Boolean reprint = false;
    }

    public static class CodeRenderRequest {
        public String text;
        public String format = "QR_CODE";
        public Integer width = 360;
        public Integer height = 140;
        public Boolean includeText = true;
    }

    public static class CodeRenderResponse {
        public String text;
        public String format;
        public String svg;
        public String dataUri;
    }

    public static class PreviewResponse {
        public String labelCode;
        public String labelType;
        public String codeCarrierType;
        public String templateCode;
        public String primaryScanValue;
        public String secondaryScanValue;
        public String barcodeValue;
        /** V0.6 真实标签字段：仓库代码，条形码默认扫出来就是这个值。 */
        public String warehouseCode;
        public String warehouseAddress;
        public String sendStationAddress;
        public String boxSize;
        public String delivererEmployeeNo;
        public String kanbanCardNo;
        public String areaCode;
        public String projectCode;
        public String routeName;
        public String deliveryAddress;
        public String businessCode;
        public String gridCode;
        public String pointOfUseAddress;
        public String routing;
        public Integer cardNo;
        public Integer cardTotal;
        public String supermarketBusiness;
        public String supermarketGrid;
        public String supermarketAddress;
        public String materialCode;
        public String materialName;
        public String materialImageUrl;
        public BigDecimal standardQty;
        public String boxSide;
        public String labelUsageType;
        public String deliveryMode;
        public String containerType;
        public String warehouseLocation;
        public String specText;
        public String printDate;
        public String status;
        public String rawPayload;
        public String fieldSnapshotJson;
    }
}
