package com.example.materialpull.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "log_interface")
public class InterfaceLogEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String interfaceName;
    private String direction;
    @Column(length = 4000) private String requestBody;
    @Column(length = 4000) private String responseBody;
    private Boolean success;
    private String message;
    private LocalDateTime createdAt;
    @PrePersist public void prePersist(){ createdAt = LocalDateTime.now(); }
}
