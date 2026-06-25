package com.example.materialpull.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "m_label_scan_rule", indexes = {
        @Index(name = "idx_scan_rule_code", columnList = "ruleCode", unique = true),
        @Index(name = "idx_scan_rule_type", columnList = "labelType,carrierType,enabled")
})
public class LabelScanRuleEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Version private Long version;

    /** 规则编码，例如 RULE_NUMERIC_KANBAN、RULE_QR_JSON、RULE_POINT_OF_USE_MANUAL。 */
    private String ruleCode;
    private String ruleName;
    /** 标签类型，允许后续扩展，不使用枚举写死。 */
    private String labelType;
    /** 承载介质：BARCODE_1D / QR_CODE / DATA_MATRIX / MANUAL / NONE。 */
    private String carrierType;
    /** 优先级越小越先匹配。 */
    private Integer priorityNo = 100;
    /** 匹配方式：EXACT / REGEX / PREFIX / JSON / KEY_VALUE / ALWAYS。 */
    private String matcherType = "REGEX";
    /** 正则、前缀或字段名。JSON/KEY_VALUE 可为空。 */
    @Column(length = 1000)
    private String matcherPattern;
    /** 扫码内容格式：PLAIN / NUMBER / JSON / KEY_VALUE / COMPOSITE。 */
    private String payloadFormat = "PLAIN";
    /** 解析后默认落到哪个字段：primaryScanValue / kanbanCardNo / materialCode / labelCode。 */
    private String targetField = "primaryScanValue";
    /** 是否允许一条规则对应多个标签，通常扫码主键必须 false。 */
    private Boolean allowMultipleMatch = false;
    /** 未建档时是否允许生成待确认占位标签，生产默认 false。 */
    private Boolean allowCreateStub = false;
    private Boolean enabled = true;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @PrePersist public void prePersist(){ createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate public void preUpdate(){ updatedAt = LocalDateTime.now(); }
}
