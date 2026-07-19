package com.example.materialpull.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "t_box", indexes = {
        @Index(name = "idx_box_code", columnList = "boxCode", unique = true),
        @Index(name = "idx_box_station_material", columnList = "stationCode,materialCode"),
        @Index(name = "idx_box_kanban", columnList = "kanbanCardNo"),
        @Index(name = "idx_box_barcode", columnList = "barcodeValue"),
        @Index(name = "idx_box_warehouse_code", columnList = "warehouseCode")
})
public class BoxEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Version private Long version;
    private String boxCode;
    private String pairCode;
    private String boxSide;
    private String labelCode;
    /** 现场扫码枪扫出的条码。V0.6 真实标签中，条码等于仓库代码。 */
    private String barcodeValue;
    private String warehouseCode;
    private String warehouseAddress;
    private String sendStationAddress;
    private String boxSize;
    private String delivererEmployeeNo;
    private String kanbanCardNo;
    private String areaCode;
    private String lineCode;
    private String stationCode;
    private String stationName;
    private String projectCode;
    private String routeName;
    private String deliveryAddress;
    private String warehouseLocation;
    private String materialCode;
    private String materialName;
    private String warehouseMaterialCode;
    private BigDecimal standardQty = BigDecimal.ZERO;
    private BigDecimal currentQty = BigDecimal.ZERO;
    @Enumerated(EnumType.STRING) private com.example.materialpull.enums.BoxStatus status = com.example.materialpull.enums.BoxStatus.FULL_STANDBY;
    private Integer cycleCount = 0;
    private LocalDateTime lastScanAt;
    private LocalDateTime lastReplenishedAt;
    private String healthStatus = "OK";
    private String lockReason;
    private LocalDateTime lockedAt;
    private String lastError;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @PrePersist public void prePersist(){ createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate public void preUpdate(){ updatedAt = LocalDateTime.now(); }
}
