package com.example.materialpull.entity;

import com.example.materialpull.enums.AgvJobStatus;
import com.example.materialpull.enums.PriorityLevel;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "t_agv_job", indexes = {
        @Index(name = "idx_agv_job_no", columnList = "agvJobNo", unique = true),
        @Index(name = "idx_agv_task", columnList = "taskNo"),
        @Index(name = "idx_agv_status", columnList = "status")
})
public class AgvJobEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Version private Long version;
    private String agvJobNo;
    private String taskNo;
    private String jobType;
    private String containerNo;
    private String fromLocation;
    private String toLocation;
    private String externalJobNo;
    @Enumerated(EnumType.STRING) private PriorityLevel priority = PriorityLevel.NORMAL;
    @Enumerated(EnumType.STRING) private AgvJobStatus status = AgvJobStatus.CREATED;
    @Column(length = 4000) private String requestPayload;
    @Column(length = 4000) private String responsePayload;
    private String lastError;
    private LocalDateTime sentAt;
    private LocalDateTime arrivedAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @PrePersist public void prePersist(){ createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate public void preUpdate(){ updatedAt = LocalDateTime.now(); }
}
