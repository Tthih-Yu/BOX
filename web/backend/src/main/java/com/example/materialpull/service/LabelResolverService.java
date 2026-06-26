package com.example.materialpull.service;

import com.example.materialpull.common.BusinessException;
import com.example.materialpull.common.ErrorCode;
import com.example.materialpull.entity.LabelEntity;
import com.example.materialpull.entity.LabelScanRuleEntity;
import com.example.materialpull.entity.MaterialMappingEntity;
import com.example.materialpull.enums.LabelStatus;
import com.example.materialpull.repository.LabelRepository;
import com.example.materialpull.repository.LabelScanRuleRepository;
import com.example.materialpull.repository.MaterialMappingRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class LabelResolverService {
    private final LabelRepository labelRepository;
    private final LabelScanRuleRepository ruleRepository;
    private final MaterialMappingRepository mappingRepository;
    private final ObjectMapper objectMapper;

    public String normalize(String scanCode) {
        if (scanCode == null) throw new BusinessException(ErrorCode.PARAM_ERROR, "扫码内容不能为空");
        String s = scanCode.trim();
        if (s.isBlank()) throw new BusinessException(ErrorCode.PARAM_ERROR, "扫码内容不能为空");
        s = s.replace("\uFEFF", "").replace("\r", "").replace("\n", "").replace("\t", "").trim();
        if (s.length() > 2048) throw new BusinessException(ErrorCode.PARAM_ERROR, "扫码内容过长，疑似二维码内容或扫码枪配置异常，请检查码制规则");
        return s;
    }

    public LabelEntity resolve(String rawCode) {
        String code = normalize(rawCode);
        return uniqueOrVirtual(resolveCandidates(code, false), code);
    }

    public LabelEntity resolveForUpdate(String rawCode) {
        String code = normalize(rawCode);
        return uniqueOrVirtual(resolveCandidates(code, true), code);
    }

    private List<LabelEntity> resolveCandidates(String code, boolean forUpdate) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        keys.add(code);
        keys.addAll(extractFromJson(code));
        keys.addAll(extractFromKeyValue(code));
        keys.addAll(applyRules(code));
        LinkedHashMap<Long, LabelEntity> result = new LinkedHashMap<>();
        for (String k : keys) {
            if (k == null || k.isBlank()) continue;
            List<LabelEntity> list = forUpdate ? labelRepository.findByAnyCodeForUpdate(k.trim()) : labelRepository.findByAnyCode(k.trim());
            for (LabelEntity l : list) {
                enrichFromMapping(l, code);
                result.put(l.getId(), l);
            }
        }
        return new ArrayList<>(result.values());
    }

    private LabelEntity uniqueOrVirtual(List<LabelEntity> list, String code) {
        if (list.size() == 1) return list.get(0);
        if (list.size() > 1) {
            String labels = list.stream().limit(5).map(LabelEntity::getLabelCode).toList().toString();
            throw new BusinessException(ErrorCode.DATA_DIRTY, "扫码值对应多张标签，禁止继续生成任务。请在标签中心处理重复码：" + code + "，命中=" + labels);
        }
        return virtualSiteLabel(code).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "未找到标签/二维码/仓库代码/看板卡：" + code + "。现场二维码需包含 materialCode 或 MAT 字段，或先在标签中心建档。"));
    }

    private Optional<LabelEntity> virtualSiteLabel(String rawCode) {
        Map<String, String> fields = parseFields(rawCode);
        String materialCode = firstNonBlank(fields.get("materialCode"), fields.get("MAT"), fields.get("MATERIAL"), rawCode.matches("\\d{6,}") ? rawCode : null);
        if (materialCode == null) return Optional.empty();
        List<MaterialMappingEntity> mappings = mappingRepository.findByLineMaterialCodeAndEnabledTrueOrderByIdAsc(materialCode);
        MaterialMappingEntity mapping = mappings.isEmpty() ? null : mappings.get(0);
        LabelEntity label = new LabelEntity();
        label.setLabelCode("QR-" + materialCode + "-" + Integer.toHexString(rawCode.hashCode()).toUpperCase(Locale.ROOT));
        label.setLabelType("SITE_QR_MATERIAL");
        label.setCodeCarrierType("QR_CODE");
        label.setTemplateCode("SITE_QR_MATERIAL");
        label.setPrimaryScanValue(rawCode);
        label.setRawPayload(rawCode);
        label.setStatus(LabelStatus.BOUND);
        label.setMaterialCode(materialCode);
        label.setMaterialName(firstNonBlank(fields.get("materialName"), fields.get("NAME"), materialCode));
        label.setWarehouseMaterialCode(mapping == null ? materialCode : mapping.getWarehouseMaterialCode());
        label.setWarehouseCode(mapping == null ? null : mapping.getWarehouseCode());
        label.setBoxSize(mapping == null ? null : mapping.getBoxSize());
        label.setStandardQty(mapping == null ? java.math.BigDecimal.ONE : mapping.getStandardQty());
        label.setLabelUsageType(mapping == null ? "USE" : mapping.getLabelUsageType());
        label.setDeliveryMode(mapping == null ? "NORMAL" : mapping.getDeliveryMode());
        label.setDeliveryAddress(firstNonBlank(fields.get("materialAddress"), fields.get("address"), fields.get("ADDR"), fields.get("station")));
        label.setSendStationAddress(label.getDeliveryAddress());
        return Optional.of(labelRepository.save(label));
    }

    private void enrichFromMapping(LabelEntity label, String rawCode) {
        if (label == null || label.getMaterialCode() == null || label.getMaterialCode().isBlank()) return;
        if (label.getWarehouseCode() != null && !label.getWarehouseCode().isBlank()) return;
        List<MaterialMappingEntity> mappings = mappingRepository.findByLineMaterialCodeAndEnabledTrueOrderByIdAsc(label.getMaterialCode());
        if (mappings.isEmpty()) return;
        MaterialMappingEntity m = mappings.stream()
                .filter(x -> nullToBlank(label.getLabelUsageType()).equalsIgnoreCase(nullToBlank(x.getLabelUsageType())))
                .findFirst().orElse(mappings.get(0));
        label.setWarehouseCode(m.getWarehouseCode());
        label.setWarehouseMaterialCode(firstNonBlank(label.getWarehouseMaterialCode(), m.getWarehouseMaterialCode()));
        label.setBoxSize(firstNonBlank(label.getBoxSize(), m.getBoxSize()));
        if (label.getStandardQty() == null || label.getStandardQty().compareTo(java.math.BigDecimal.ZERO) <= 0) label.setStandardQty(m.getStandardQty());
        label.setLabelUsageType(firstNonBlank(label.getLabelUsageType(), m.getLabelUsageType(), "USE"));
        label.setDeliveryMode(firstNonBlank(label.getDeliveryMode(), m.getDeliveryMode(), "NORMAL"));
    }

    private List<String> extractFromJson(String code) {
        String s = code.trim();
        if (!(s.startsWith("{") && s.endsWith("}"))) return List.of();
        try {
            JsonNode n = objectMapper.readTree(s);
            List<String> keys = new ArrayList<>();
            for (String f : List.of("primaryScanValue", "code", "scanCode", "barcodeValue", "warehouseCode", "warehouse_code", "whCode", "wh_code", "kanbanCardNo", "labelCode", "qrCode", "materialCode")) {
                JsonNode v = n.get(f);
                if (v != null && !v.isNull() && !v.asText().isBlank()) keys.add(v.asText().trim());
            }
            return keys;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "二维码内容像JSON但无法解析，请检查二维码格式");
        }
    }

    private Map<String, String> parseFields(String code) {
        Map<String, String> map = new HashMap<>();
        String s = code.trim();
        if (s.startsWith("{") && s.endsWith("}")) {
            try {
                JsonNode n = objectMapper.readTree(s);
                n.fields().forEachRemaining(e -> map.put(e.getKey(), e.getValue().asText()));
            } catch (Exception ignored) {}
        }
        if (code.contains("=") || code.contains(":")) {
            for (String p : code.split("[;|,]")) {
                String[] kv = p.split("[:=]", 2);
                if (kv.length == 2) map.put(kv[0].trim(), kv[1].trim());
            }
        }
        return map;
    }

    private List<String> extractFromKeyValue(String code) {
        if (!(code.contains("=") || code.contains(":"))) return List.of();
        String[] parts = code.split("[;|,]");
        List<String> out = new ArrayList<>();
        Set<String> aliases = Set.of("CODE","SCANCODE","SCAN_CODE","PRIMARY","PRIMARYSCANVALUE","BARCODE","BARCODEVALUE","WAREHOUSECODE","WHCODE","WH","WAREHOUSE","STORECODE","KANBAN","KANBANCARDNO","LABEL","LABELCODE","MAT","MATERIAL","MATERIALCODE");
        for (String p : parts) {
            String[] kv = p.split("[:=]", 2);
            if (kv.length != 2) continue;
            String k = kv[0].trim().replace("_", "").toUpperCase(Locale.ROOT);
            String v = kv[1].trim();
            if (aliases.contains(k) && !v.isBlank()) out.add(v);
        }
        return out;
    }

    private List<String> applyRules(String code) {
        List<String> out = new ArrayList<>();
        for (LabelScanRuleEntity r : ruleRepository.findByEnabledTrueOrderByPriorityNoAscIdAsc()) {
            String matcher = r.getMatcherType() == null ? "REGEX" : r.getMatcherType().trim().toUpperCase(Locale.ROOT);
            String pattern = r.getMatcherPattern();
            boolean matched = switch (matcher) {
                case "ALWAYS" -> true;
                case "EXACT" -> Objects.equals(code, pattern);
                case "PREFIX" -> pattern != null && code.startsWith(pattern);
                case "JSON" -> code.trim().startsWith("{") && code.trim().endsWith("}");
                case "KEY_VALUE" -> code.contains("=") || code.contains(":");
                default -> pattern != null && Pattern.matches(pattern, code);
            };
            if (matched) {
                out.add(code);
                if ("PREFIX".equals(matcher) && pattern != null && code.length() > pattern.length()) out.add(code.substring(pattern.length()).trim());
            }
        }
        return out;
    }

    private String firstNonBlank(String... values) { if (values == null) return null; for (String v : values) if (v != null && !v.isBlank()) return v.trim(); return null; }
    private String nullToBlank(String value) { return value == null ? "" : value.trim(); }
}
