package com.example.materialpull.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "t_replenishment_task", indexes = {
        @Index(name = "idx_task_no", columnList = "taskNo", unique = true),
        @Index(name = "idx_task_status", columnList = "status"),
        @Index(name = "idx_task_station_material", columnList = "stationCode,materialCode"),
        @Index(name = "idx_task_kanban", columnList = "kanbanCardNo"),
        @Index(name = "idx_task_warehouse_code", columnList = "warehouseCode"),
        @Index(name = "idx_task_delivery", columnList = "projectCode,routeName,deliveryAddress"),
        @Index(name = "idx_task_priority", columnList = "priority"),
        @Index(name = "idx_task_deadline", columnList = "deadlineAt"),
        @Index(name = "idx_task_plan", columnList = "planNo,demandNo")
})
public class ReplenishmentTaskEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Version private Long version;
    private String taskNo;
    private String sourceLabelCode;
    private String barcodeValue;
    private String warehouseCode;
    private String warehouseAddress;
    private String sendStationAddress;
    private String boxSize;
    private String delivererEmployeeNo;
    private String kanbanCardNo;
    private String boxCode;
    private String pairCode;
    private String boxSide;
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
    private String materialImageUrl;
    private BigDecimal requestQty = BigDecimal.ZERO;
    @Enumerated(EnumType.STRING) private com.example.materialpull.enums.TaskStatus status = com.example.materialpull.enums.TaskStatus.CREATED;
    @Enumerated(EnumType.STRING) private com.example.materialpull.enums.PriorityLevel priority = com.example.materialpull.enums.PriorityLevel.NORMAL;
    private String createdBy;
    private String acceptedBy;
    private String picker;
    private String deliverer;
    private String receivedBy;
    private String receiveScanCode;
    private String containerNo;
    private String emptyContainerNo;
    /** 兼容旧页面的配送 AGV 作业号。 */
    private String agvJobNo;
    /** 满箱配送 AGV 作业号。 */
    private String deliveryAgvJobNo;
    /** 空箱回收 AGV 作业号，避免覆盖满箱配送作业号。 */
    private String returnAgvJobNo;
    private String printJobNo;
    private String planNo;
    private String demandNo;
    private String labelUsageType;
    private String deliveryMode;
    private Boolean singleBoxTask = false;
    private Integer boxSeq = 0;
    private Integer boxTotal = 0;
    private Boolean emptyBoxReturnRequired = false;
    private Boolean agvDispatched = false;
    private Boolean printGenerated = false;
    private String exceptionReason;
    private Boolean inventoryLocked = false;
    private Boolean inventoryDeducted = false;
    private BigDecimal lockedQty = BigDecimal.ZERO;
    private Integer actionSeq = 0;
    private Integer retryCount = 0;
    private Boolean stuckFlag = false;
    private String idempotencyKey;
    private String lastError;
    private LocalDateTime acceptedAt;
    private LocalDateTime pickedAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime arrivedAt;
    private LocalDateTime receivedAt;
    private LocalDateTime completedAt;
    private LocalDateTime deadlineAt;
    private LocalDateTime lastActionAt;
    private LocalDateTime warningAt;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @PrePersist public void prePersist(){ createdAt = updatedAt = LocalDateTime.now(); normalize(); }
    @PreUpdate public void preUpdate(){ updatedAt = LocalDateTime.now(); normalize(); }
    private void normalize(){
        if (emptyBoxReturnRequired == null) emptyBoxReturnRequired = false;
        if (agvDispatched == null) agvDispatched = false;
        if (printGenerated == null) printGenerated = false;
        if (inventoryLocked == null) inventoryLocked = false;
        if (inventoryDeducted == null) inventoryDeducted = false;
        if (singleBoxTask == null) singleBoxTask = false;
        if (boxSeq == null) boxSeq = 0;
        if (boxTotal == null) boxTotal = 0;
        if (requestQty == null) requestQty = BigDecimal.ZERO;
        if (lockedQty == null) lockedQty = BigDecimal.ZERO;
    }
}
