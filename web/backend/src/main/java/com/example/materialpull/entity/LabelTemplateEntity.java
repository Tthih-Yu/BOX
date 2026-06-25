package com.example.materialpull.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "m_label_template", indexes = {
        @Index(name = "idx_template_code", columnList = "templateCode", unique = true),
        @Index(name = "idx_template_type", columnList = "labelType,carrierType,enabled")
})
public class LabelTemplateEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String templateCode;
    private String templateName;
    private String labelType = "SITE_KANBAN_BARCODE";
    private String carrierType = "BARCODE_1D";
    /** 预览渲染类型：SITE_KANBAN、POINT_OF_USE、GENERIC_QR、GENERIC_TABLE。 */
    private String previewRenderer = "SITE_KANBAN";
    private Integer widthMm = 80;
    private Integer heightMm = 50;
    @Column(length = 4000) private String content;
    /** 模板字段定义 JSON，记录哪些字段必填、显示名称、顺序、字体大小等。 */
    @Column(length = 4000) private String schemaJson;
    /** 打印指令模板，可存 ZPL/TSPL/HTML，真实打印服务接入时直接使用。 */
    @Column(length = 8000) private String printCommandTemplate;
    private Boolean enabled = true;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @PrePersist public void prePersist(){ createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate public void preUpdate(){ updatedAt = LocalDateTime.now(); }
}
