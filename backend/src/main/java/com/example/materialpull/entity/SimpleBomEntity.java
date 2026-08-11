package com.example.materialpull.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "t_simple_bom", indexes = {
        @Index(name = "idx_sbom_batch", columnList = "batchNo"),
        @Index(name = "idx_sbom_batch_material", columnList = "batchNo,materialCode"),
        @Index(name = "idx_sbom_batch_component", columnList = "batchNo,componentCode")
})
public class SimpleBomEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    /** 所属导入批次。 */
    private String batchNo;
    /** 物料8D号(8位纯数字)。 */
    private String materialCode;
    /** 组件8D号(8位纯数字)，即料号映射 lineMaterialCode。 */
    private String componentCode;
    private LocalDateTime createdAt;
    @PrePersist public void prePersist(){ if (createdAt == null) createdAt = LocalDateTime.now(); }
}
