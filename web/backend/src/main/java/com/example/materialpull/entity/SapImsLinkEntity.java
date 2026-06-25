package com.example.materialpull.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "t_sap_ims_link", indexes = {
        @Index(name = "idx_link_no", columnList = "linkNo", unique = true),
        @Index(name = "idx_link_system", columnList = "systemCode,externalKey"),
        @Index(name = "idx_link_material", columnList = "materialCode,warehouseMaterialCode")
})
public class SapImsLinkEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Version private Long version;
    private String linkNo;
    private String systemCode;
    private String externalKey;
    private String productCode;
    private String materialCode;
    private String warehouseMaterialCode;
    private String warehouseCode;
    private String processCode;
    private String lineCode;
    private String stationCode;
    private Boolean enabled = true;
    private LocalDateTime lastSyncAt;
    @Column(length = 4000) private String rawPayload;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @PrePersist public void prePersist(){ createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate public void preUpdate(){ updatedAt = LocalDateTime.now(); }
}
