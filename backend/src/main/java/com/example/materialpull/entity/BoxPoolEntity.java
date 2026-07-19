package com.example.materialpull.entity;

import com.example.materialpull.enums.BoxPoolStatus;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "t_box_pool", indexes = {
        @Index(name = "idx_pool_container_no", columnList = "containerNo", unique = true),
        @Index(name = "idx_pool_status", columnList = "status"),
        @Index(name = "idx_pool_type", columnList = "containerType,boxSize"),
        @Index(name = "idx_pool_task", columnList = "taskNo")
})
public class BoxPoolEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Version private Long version;
    private String containerNo;
    private String containerType;
    private String boxSize;
    private String materialCode;
    private String warehouseMaterialCode;
    private String warehouseCode;
    private String taskNo;
    private String stationCode;
    private String currentLocation;
    @Enumerated(EnumType.STRING) private BoxPoolStatus status = BoxPoolStatus.AVAILABLE;
    private Boolean lockedFlag = false;
    private Integer cycleCount = 0;
    private LocalDateTime lastAllocatedAt;
    private LocalDateTime lastDeliveredAt;
    private LocalDateTime lastReturnedAt;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @PrePersist public void prePersist(){ createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate public void preUpdate(){ updatedAt = LocalDateTime.now(); }
}
