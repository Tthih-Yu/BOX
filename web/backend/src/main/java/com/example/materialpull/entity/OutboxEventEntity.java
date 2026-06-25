package com.example.materialpull.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "sys_outbox_event", indexes = {
        @Index(name = "idx_outbox_status", columnList = "status"),
        @Index(name = "idx_outbox_topic", columnList = "topic")
})
public class OutboxEventEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String eventNo;
    private String topic;
    private String businessNo;
    private String status = "NEW";
    private Integer retryCount = 0;
    @Column(length = 4000) private String payload;
    @Column(length = 2000) private String lastError;
    private LocalDateTime nextRetryAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @PrePersist public void prePersist(){ createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate public void preUpdate(){ updatedAt = LocalDateTime.now(); }
}
