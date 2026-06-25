package com.example.materialpull.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "t_label", indexes = {
        @Index(name = "idx_label_code", columnList = "labelCode", unique = true),
        @Index(name = "idx_label_barcode", columnList = "barcodeValue", unique = true),
        @Index(name = "idx_label_kanban", columnList = "kanbanCardNo", unique = true),
        @Index(name = "idx_label_site", columnList = "projectCode,routeName,deliveryAddress"),
        @Index(name = "idx_label_material_box", columnList = "materialCode,boxSide"),
        @Index(name = "idx_label_primary_scan", columnList = "primaryScanValue", unique = true),
        @Index(name = "idx_label_warehouse_code", columnList = "warehouseCode", unique = true),
        @Index(name = "idx_label_type", columnList = "labelType,codeCarrierType"),
        @Index(name = "idx_label_pou", columnList = "businessCode,pointOfUseAddress")
})
public class LabelEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Version private Long version;

    private String labelCode;
    private String labelType = "SITE_KANBAN_BARCODE";
    private String codeCarrierType = "BARCODE_1D";
    private String primaryScanValue;
    private String secondaryScanValue;
    @Column(length = 4000) private String rawPayload;
    private String barcodeValue;
    private String warehouseCode;
    private String warehouseAddress;
    private String sendStationAddress;
    private String boxSize;
    private String delivererEmployeeNo;
    private String kanbanCardNo;
    private String areaCode;

    private String boxCode;
    private String pairCode;
    private String boxSide;
    private String labelUsageType;
    private String deliveryMode;
    private String lineCode;
    private String stationCode;
    private String stationName;

    private String projectCode;
    private String routeName;
    private String deliveryAddress;
    private String businessCode;
    private String gridCode;
    private String pointOfUseAddress;
    private String routing;
    private Integer cardNo;
    private Integer cardTotal;
    private String supermarketBusiness;
    private String supermarketGrid;
    private String supermarketAddress;

    private String materialCode;
    private String materialName;
    private String warehouseMaterialCode;
    private String materialImageUrl;
    private BigDecimal standardQty = BigDecimal.ZERO;
    private String unit;
    private String specText;
    private String warehouseLocation;
    private String containerType;

    private String templateCode;
    private String templateVersion;
    @Column(length = 4000) private String fieldSnapshotJson;
    @Enumerated(EnumType.STRING) private com.example.materialpull.enums.LabelStatus status = com.example.materialpull.enums.LabelStatus.UNUSED;
    private Integer printCount = 0;
    private LocalDate printDate;
    private LocalDateTime lastPrintedAt;
    private String lastPrintUser;
    private LocalDateTime lastScannedAt;
    private String lastScanDevice;
    private String lastScanOperator;
    private String lastError;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        createdAt = updatedAt = LocalDateTime.now();
        normalizeBeforeSave();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
        normalizeBeforeSave();
    }

    private void normalizeBeforeSave() {
        if (printDate == null) printDate = LocalDate.now();
        labelCode = blankToNull(labelCode);
        labelType = blankToNull(labelType);
        codeCarrierType = blankToNull(codeCarrierType);
        primaryScanValue = blankToNull(primaryScanValue);
        secondaryScanValue = blankToNull(secondaryScanValue);
        barcodeValue = blankToNull(barcodeValue);
        warehouseCode = blankToNull(warehouseCode);
        kanbanCardNo = blankToNull(kanbanCardNo);
        boxSide = blankToNull(boxSide);
        containerType = blankToNull(containerType);
        if (primaryScanValue == null) primaryScanValue = firstNonBlank(barcodeValue, kanbanCardNo, labelCode);
        if ("FACTORY_PULL_BARCODE".equalsIgnoreCase(labelType)) {
            if (warehouseCode == null) warehouseCode = firstNonBlank(barcodeValue, primaryScanValue);
            if (barcodeValue == null) barcodeValue = warehouseCode;
            if (primaryScanValue == null) primaryScanValue = warehouseCode;
        }
        if (labelUsageType == null) labelUsageType = boxSide == null ? "USE" : ("SPARE".equalsIgnoreCase(boxSide) || "B".equalsIgnoreCase(boxSide) ? "SPARE" : "USE");
        if (deliveryMode == null) deliveryMode = "SPARE".equalsIgnoreCase(labelUsageType) ? "URGENT" : "NORMAL";
        if (containerType == null) containerType = boxSide;
        if (standardQty == null) standardQty = BigDecimal.ZERO;
        if (printCount == null) printCount = 0;
    }

    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) if (value != null && !value.isBlank()) return value.trim();
        return null;
    }
}
