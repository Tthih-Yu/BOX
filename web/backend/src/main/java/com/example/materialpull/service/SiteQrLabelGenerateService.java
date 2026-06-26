package com.example.materialpull.service;

import com.example.materialpull.common.BusinessException;
import com.example.materialpull.common.ErrorCode;
import com.example.materialpull.common.IdGenerator;
import com.example.materialpull.common.OperatorResolver;
import com.example.materialpull.dto.LabelDtos;
import com.example.materialpull.entity.LabelEntity;
import com.example.materialpull.entity.MaterialMappingEntity;
import com.example.materialpull.entity.StationMaterialEntity;
import com.example.materialpull.enums.LabelStatus;
import com.example.materialpull.repository.LabelRepository;
import com.example.materialpull.repository.MaterialMappingRepository;
import com.example.materialpull.repository.StationMaterialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class SiteQrLabelGenerateService {
    private final StationMaterialRepository stationMaterialRepository;
    private final MaterialMappingRepository mappingRepository;
    private final LabelRepository labelRepository;
    private final AuditService auditService;

    @Transactional
    public List<LabelEntity> generate(LabelDtos.GenerateRequest req) {
        if (req == null) req = new LabelDtos.GenerateRequest();
        if (req.stationCode == null || req.stationCode.isBlank()) throw new BusinessException(ErrorCode.PARAM_ERROR, "工位不能为空");
        if (req.materialCode == null || req.materialCode.isBlank()) throw new BusinessException(ErrorCode.PARAM_ERROR, "物料不能为空");
        StationMaterialEntity station = stationMaterialRepository.findByStationCodeAndMaterialCodeAndEnabledTrue(req.stationCode.trim(), req.materialCode.trim())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "工位用料不存在：" + req.stationCode + " / " + req.materialCode));
        int count = req.count == null ? 1 : Math.max(1, Math.min(200, req.count));
        List<MaterialMappingEntity> mappings = mappingRepository.findByLineMaterialCodeAndEnabledTrueOrderByIdAsc(station.getMaterialCode());
        List<LabelEntity> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            MaterialMappingEntity map = mappings.isEmpty() ? null : mappings.get(Math.min(i, mappings.size() - 1));
            String usage = map == null ? (i == 0 ? "USE" : "SPARE") : map.getLabelUsageType();
            String mode = map == null ? ("SPARE".equalsIgnoreCase(usage) ? "URGENT" : "NORMAL") : map.getDeliveryMode();
            String qrText = qrPayload(station, map, usage, mode);
            String labelCode = "SITEQR-" + station.getStationCode() + "-" + station.getMaterialCode() + "-" + Integer.toHexString(qrText.hashCode()).toUpperCase(Locale.ROOT);
            LabelEntity label = labelRepository.findByLabelCode(labelCode).orElseGet(LabelEntity::new);
            label.setLabelCode(labelCode);
            label.setLabelType("SITE_QR_MATERIAL");
            label.setCodeCarrierType("QR_CODE");
            label.setTemplateCode("SITE_QR_MATERIAL_V1");
            label.setPrimaryScanValue(qrText);
            label.setRawPayload(qrText);
            label.setStatus(LabelStatus.BOUND);
            label.setLineCode(station.getLineCode());
            label.setStationCode(station.getStationCode());
            label.setStationName(station.getStationName());
            label.setProjectCode(station.getProjectCode());
            label.setRouteName(station.getRouteName());
            label.setDeliveryAddress(station.getDeliveryAddress());
            label.setSendStationAddress(station.getDeliveryAddress());
            label.setAreaCode(station.getAreaCode());
            label.setWarehouseLocation(station.getWarehouseLocation());
            label.setSpecText(station.getSpecText());
            label.setUnit(station.getUnit());
            label.setMaterialCode(station.getMaterialCode());
            label.setMaterialName(station.getMaterialName());
            label.setWarehouseMaterialCode(map == null ? station.getWarehouseMaterialCode() : map.getWarehouseMaterialCode());
            label.setWarehouseCode(map == null ? null : map.getWarehouseCode());
            label.setBoxSize(map == null ? null : map.getBoxSize());
            label.setStandardQty(quantity(station, map));
            label.setLabelUsageType(usage);
            label.setDeliveryMode(mode);
            label.setRemark("现场二维码标签：二维码包含物料号、物料名称、物料地址；仓库代号来自Excel映射");
            out.add(labelRepository.save(label));
        }
        auditService.print(station.getStationCode() + "/" + station.getMaterialCode(), "GENERATE_SITE_QR_LABEL", OperatorResolver.currentOperator(), null, true, "生成现场二维码标签 " + out.size() + " 张");
        return out;
    }

    private String qrPayload(StationMaterialEntity s, MaterialMappingEntity m, String usage, String mode) {
        String materialAddress = firstNonBlank(s.getDeliveryAddress(), s.getStationName(), s.getStationCode());
        return "物料号=" + safe(s.getMaterialCode()) + ";物料名称=" + safe(s.getMaterialName()) + ";物料地址=" + safe(materialAddress) + ";usage=" + safe(usage) + ";deliveryMode=" + safe(mode) + ";warehouseCode=" + safe(m == null ? null : m.getWarehouseCode());
    }

    private BigDecimal quantity(StationMaterialEntity s, MaterialMappingEntity m) {
        if (m != null && m.getStandardQty() != null && m.getStandardQty().compareTo(BigDecimal.ZERO) > 0) return m.getStandardQty();
        if (s.getStandardBoxQty() != null && s.getStandardBoxQty().compareTo(BigDecimal.ZERO) > 0) return s.getStandardBoxQty();
        return BigDecimal.ONE;
    }

    private String safe(String value) { return value == null ? "" : value.replace(";", " ").replace("=", " ").trim(); }
    private String firstNonBlank(String... values) { if (values == null) return null; for (String v : values) if (v != null && !v.isBlank()) return v.trim(); return null; }
}
