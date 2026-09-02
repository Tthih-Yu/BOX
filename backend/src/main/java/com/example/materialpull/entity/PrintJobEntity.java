package com.example.materialpull.entity;

import com.example.materialpull.enums.PrintJobStatus;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "t_print_job", indexes = {
        @Index(name = "idx_print_job_no", columnList = "printJobNo", unique = true),
        @Index(name = "idx_print_task", columnList = "taskNo"),
        @Index(name = "idx_print_status", columnList = "status"),
        @Index(name = "idx_print_scope", columnList = "factory,deliveryArea")
})
public class PrintJobEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Version private Long version;
    private String printJobNo;
    /** 从任务继承的工厂快照；空值表示历史或未维护工厂。 */
    private String factory;
    /** 从任务继承的配送区域快照；不从标签 areaCode 或请求参数推断。 */
    private String deliveryArea;
    private String taskNo;
    private String labelCode;
    private String printType;
    /** 打印方式：BROWSER=浏览器打印，AGENT=本地代理自动打印。用于区分完成来源。 */
    private String printChannel;
    private String printerName;
    private String operator;
    private String externalJobNo;
    @Column(length = 4000) private String payload;
    @Column(length = 4000) private String zplContent;
    @Column(length = 4000) private String responsePayload;
    @Enumerated(EnumType.STRING) private PrintJobStatus status = PrintJobStatus.CREATED;
    private String lastError;
    private LocalDateTime sentAt;
    private LocalDateTime printedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @PrePersist public void prePersist(){ createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate public void preUpdate(){ updatedAt = LocalDateTime.now(); }
}
