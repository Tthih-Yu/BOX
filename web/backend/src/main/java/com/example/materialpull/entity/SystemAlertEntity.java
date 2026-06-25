package com.example.materialpull.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "sys_alert", indexes = {
        @Index(name = "idx_alert_status", columnList = "status"),
        @Index(name = "idx_alert_level", columnList = "level")
})
public class SystemAlertEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String alertNo;
    private String level;
    private String category;
    private String businessNo;
    private String title;
    @Column(length = 4000) private String content;
    private String status = "OPEN";
    private String owner;
    private String closeRemark;
    private LocalDateTime closedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @PrePersist public void prePersist(){ createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate public void preUpdate(){ updatedAt = LocalDateTime.now(); }
}
