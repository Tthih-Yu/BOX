package com.example.materialpull.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "t_exception_event")
public class ExceptionEventEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String eventNo;
    private String sourceType;
    private String sourceNo;
    private String level;
    private String title;
    @Column(length = 2000) private String content;
    @Enumerated(EnumType.STRING) private com.example.materialpull.enums.ExceptionStatus status = com.example.materialpull.enums.ExceptionStatus.OPEN;
    private String owner;
    private String closeRemark;
    private LocalDateTime closedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @PrePersist public void prePersist(){ createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate public void preUpdate(){ updatedAt = LocalDateTime.now(); }
}
