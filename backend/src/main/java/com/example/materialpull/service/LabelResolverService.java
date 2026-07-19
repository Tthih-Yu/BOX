package com.example.materialpull.service;

import com.example.materialpull.common.BusinessException;
import com.example.materialpull.common.ErrorCode;
import com.example.materialpull.entity.LabelEntity;
import com.example.materialpull.entity.LabelScanRuleEntity;
import com.example.materialpull.repository.LabelRepository;
import com.example.materialpull.repository.LabelScanRuleRepository;
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
    private final ObjectMapper objectMapper;

    public String normalize(String scanCode) {
        if (scanCode == null) throw new BusinessException(ErrorCode.PARAM_ERROR, "扫码内容不能为空");
        String s = scanCode.trim();
        if (s.isBlank()) throw new BusinessException(ErrorCode.PARAM_ERROR, "扫码内容不能为空");
        // 扫码枪常见尾缀：回车、Tab、不可见BOM。二维码可能是JSON/键值对，所以只清理边界和控制符，不误删有效分隔符。
        s = s.replace("\uFEFF", "").replace("\r", "").replace("\n", "").replace("\t", "").trim();
        if (s.length() > 2048) throw new BusinessException(ErrorCode.PARAM_ERROR, "扫码内容过长，疑似二维码内容或扫码枪配置异常，请检查码制规则");
        return s;
    }

    public LabelEntity resolve(String rawCode) {
        String code = normalize(rawCode);
        return unique(resolveCandidates(code, false), code);
    }

    public LabelEntity resolveForUpdate(String rawCode) {
        String code = normalize(rawCode);
        return unique(resolveCandidates(code, true), code);
    }

    /**
     * 多码制解析入口：
     * 1. 一维码纯数字，如 139315，优先查 primaryScanValue/barcodeValue/kanbanCardNo。
     * 2. 二维码 JSON，如 {"code":"139315","materialCode":"..."}，抽取 code/primaryScanValue/kanbanCardNo。
     * 3. 键值对，如 CODE=139315;TYPE=A;MAT=13736195，抽取常见字段。
     * 4. 规则表匹配，便于后续现场新增码制，不改核心流程。
     */
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
            for (LabelEntity l : list) result.put(l.getId(), l);
        }
        return new ArrayList<>(result.values());
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
                // PREFIX 类规则常用于 QR:POU:xxx，顺手把前缀后的业务码也加入候选。
                if ("PREFIX".equals(matcher) && pattern != null && code.length() > pattern.length()) {
                    out.add(code.substring(pattern.length()).trim());
                }
            }
        }
        return out;
    }

    private LabelEntity unique(List<LabelEntity> list, String code) {
        if (list.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND, "未找到标签/二维码/仓库代码/看板卡：" + code + "。系统支持一维码、二维码JSON、键值对码、内部标签编码。生产现场建议使用唯一主扫码值，不建议直接扫物料号。");
        if (list.size() > 1) {
            String labels = list.stream().limit(5).map(LabelEntity::getLabelCode).toList().toString();
            throw new BusinessException(ErrorCode.DATA_DIRTY, "扫码值对应多张标签，禁止继续生成任务。请在标签中心处理重复码：" + code + "，命中=" + labels);
        }
        return list.get(0);
    }
}
