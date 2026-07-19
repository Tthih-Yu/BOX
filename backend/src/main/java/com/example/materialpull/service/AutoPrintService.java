package com.example.materialpull.service;

import com.example.materialpull.entity.PrintJobEntity;
import com.example.materialpull.entity.ReplenishmentTaskEntity;
import com.example.materialpull.entity.SystemConfigEntity;
import com.example.materialpull.enums.TaskStatus;
import com.example.materialpull.repository.ReplenishmentTaskRepository;
import com.example.materialpull.repository.SystemConfigRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 定时自动打印标签。按“系统参数”中的开关/间隔/区域配置，
 * 周期性地为尚未打印的补货任务提交标签打印（斑马打印机）。
 * 全部配置均可在后台“系统参数”页手动修改，改完下一轮生效，无需重启。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoPrintService {
    private final SystemConfigRepository configRepository;
    private final ReplenishmentTaskRepository taskRepository;
    private final PrintJobService printJobService;
    private final com.example.materialpull.repository.MaterialMappingRepository mappingRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    static final String KEY_ENABLED = "print.auto.enabled";
    static final String KEY_INTERVAL = "print.auto.interval-minutes";
    static final String KEY_DELAY = "print.auto.delay-minutes";
    static final String KEY_PRINTER = "print.auto.printer-name";
    static final String KEY_AREAS = "print.auto.delivery-areas";
    static final String KEY_PRINT_TYPE = "print.auto.print-type";
    static final String KEY_LAST_RUN = "print.auto.last-run";
    static final String KEY_AREA_SCHEDULES = "print.auto.area-schedules";
    static final String KEY_AREA_LAST_RUN = "print.auto.area-last-run";

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 手动立即执行一轮自动打印（后台“出货标签打印”页的按钮触发）。
     * 忽略间隔与开关限制，直接按当前区域/打印机配置打印未打印任务，便于现场立即补打。
     * 若配置了分区域定时，则对所有已配置区域各打一轮。
     */
    @Transactional
    public int runNow() {
        List<AreaSchedule> schedules = areaSchedules();
        if (schedules.isEmpty()) {
            return doPrint(deliveryAreas(), trimToNull(value(KEY_PRINTER)), firstNonBlank(value(KEY_PRINT_TYPE), "WAREHOUSE_BARCODE_LABEL"), globalDelayMinutes());
        }
        int printed = 0;
        for (AreaSchedule s : schedules) {
            printed += doPrint(Set.of(s.area), firstNonBlank(s.printer, value(KEY_PRINTER)),
                    firstNonBlank(s.printType, value(KEY_PRINT_TYPE), "WAREHOUSE_BARCODE_LABEL"),
                    s.delayMinutes >= 0 ? s.delayMinutes : globalDelayMinutes());
        }
        return printed;
    }

    /**
     * 定时调度触发入口。优先走“分区域定时”：每个区域按各自的开关/间隔独立判断与记录上次执行时间；
     * 未配置分区域时，退回“全局间隔 + 区域白名单”的旧逻辑。返回本轮实际提交打印的任务数量。
     */
    @Transactional
    public int runAutoPrint() {
        List<AreaSchedule> schedules = areaSchedules();
        if (!schedules.isEmpty()) {
            return runAreaSchedules(schedules);
        }
        if (!enabled()) return 0;
        long interval = intervalMinutes();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime last = lastRun();
        if (last != null && last.plusMinutes(interval).isAfter(now)) return 0;
        int printed = doPrint(deliveryAreas(), trimToNull(value(KEY_PRINTER)), firstNonBlank(value(KEY_PRINT_TYPE), "WAREHOUSE_BARCODE_LABEL"), globalDelayMinutes());
        markLastRun(now);
        return printed;
    }

    /** 分区域定时：逐区域按各自间隔判断是否触发，并单独记录每个区域的上次执行时间。 */
    private int runAreaSchedules(List<AreaSchedule> schedules) {
        LocalDateTime now = LocalDateTime.now();
        Map<String, String> areaLastRun = readAreaLastRun();
        boolean anyRun = false;
        int printed = 0;
        for (AreaSchedule s : schedules) {
            if (!s.enabled) continue;
            LocalDateTime last = parseTs(areaLastRun.get(s.area));
            long interval = s.intervalMinutes < 1 ? 1 : s.intervalMinutes;
            if (last != null && last.plusMinutes(interval).isAfter(now)) continue;
            printed += doPrint(Set.of(s.area), firstNonBlank(s.printer, value(KEY_PRINTER)),
                    firstNonBlank(s.printType, value(KEY_PRINT_TYPE), "WAREHOUSE_BARCODE_LABEL"),
                    s.delayMinutes >= 0 ? s.delayMinutes : globalDelayMinutes());
            areaLastRun.put(s.area, now.format(TS));
            anyRun = true;
        }
        if (anyRun) writeAreaLastRun(areaLastRun);
        return printed;
    }

    private int doPrint(Set<String> areas, String printer, String printType, long delayMinutes) {
        List<TaskStatus> active = List.of(TaskStatus.CREATED, TaskStatus.ACCEPTED, TaskStatus.PICKING, TaskStatus.PICKED);
        String printerName = trimToNull(printer);
        String type = firstNonBlank(printType, "WAREHOUSE_BARCODE_LABEL");
        // 延时门槛：只提交“创建时间早于 now-delay”的任务，实现“任务出现后等 N 分钟再自动提交”。
        LocalDateTime readyBefore = delayMinutes > 0 ? LocalDateTime.now().minusMinutes(delayMinutes) : null;

        int printed = 0;
        for (ReplenishmentTaskEntity t : taskRepository.findTop500ByStatusInAndPrintGeneratedFalseOrderByCreatedAtAsc(active)) {
            if (!areaMatches(areas, t.getDeliveryArea())) continue;
            if (readyBefore != null && (t.getCreatedAt() == null || t.getCreatedAt().isAfter(readyBefore))) continue;
            try {
                PrintJobEntity job = printJobService.autoPrintForTask(t, printerName, type);
                if (job != null) printed++;
            } catch (RuntimeException e) {
                log.warn("定时自动打印失败 taskNo={} msg={}", t.getTaskNo(), e.getMessage());
            }
        }
        if (printed > 0) log.info("定时自动打印完成，区域={} 本轮提交 {} 条", areas.isEmpty() ? "全部" : areas, printed);
        return printed;
    }

    private boolean areaMatches(Set<String> areas, String area) {
        if (areas.isEmpty()) return true;
        String a = trimToNull(area);
        return a != null && areas.contains(a);
    }

    boolean enabled() {
        String v = trimToNull(value(KEY_ENABLED));
        return v != null && (v.equalsIgnoreCase("true") || v.equals("1") || v.equalsIgnoreCase("on") || v.equalsIgnoreCase("yes"));
    }

    long intervalMinutes() {
        try {
            long v = Long.parseLong(firstNonBlank(value(KEY_INTERVAL), "60"));
            return v < 1 ? 1 : v;
        } catch (NumberFormatException e) {
            return 60;
        }
    }

    /** 全局“任务出现后延时提交”分钟数。0 或非法值表示不延时。 */
    long globalDelayMinutes() {
        try {
            long v = Long.parseLong(firstNonBlank(value(KEY_DELAY), "0"));
            return v < 0 ? 0 : v;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    Set<String> deliveryAreas() {
        String v = trimToNull(value(KEY_AREAS));
        if (v == null) return Set.of();
        Set<String> set = new HashSet<>();
        for (String part : v.split("[,，;、\\s]+")) {
            String p = part.trim();
            if (!p.isEmpty()) set.add(p);
        }
        return set;
    }

    private LocalDateTime lastRun() {
        String v = trimToNull(value(KEY_LAST_RUN));
        if (v == null) return null;
        try {
            return LocalDateTime.parse(v, TS);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void markLastRun(LocalDateTime now) {
        SystemConfigEntity cfg = configRepository.findByConfigKey(KEY_LAST_RUN).orElseGet(() -> {
            SystemConfigEntity c = new SystemConfigEntity();
            c.setConfigKey(KEY_LAST_RUN);
            c.setConfigName("定时自动打印上次执行时间");
            c.setEditable(false);
            return c;
        });
        cfg.setConfigValue(now.format(TS));
        configRepository.save(cfg);
    }

    private String value(String key) {
        return configRepository.findByConfigKey(key).map(SystemConfigEntity::getConfigValue).orElse(null);
    }

    /** 供前端“定时打印设置”页读取：返回当前分区域配置(原始JSON字符串)。 */
    public String areaSchedulesJson() {
        String v = trimToNull(value(KEY_AREA_SCHEDULES));
        return v == null ? "" : v;
    }

    /** 供前端下拉：返回料号映射中出现过的全部配送区域(去重排序)，方便仓库人员按区域配置。 */
    public java.util.List<String> knownDeliveryAreas() {
        java.util.TreeSet<String> areas = new java.util.TreeSet<>();
        mappingRepository.findTop1000ByOrderByLineMaterialCodeAscMappingOrderAscIdAsc().forEach(m -> {
            String a = trimToNull(m.getDeliveryArea());
            if (a != null) areas.add(a);
        });
        return new java.util.ArrayList<>(areas);
    }

    /**
     * 供前端“定时打印设置”页保存分区域配置。会先校验JSON合法与字段有效，
     * 再规范化写回（每区域一条、间隔最小1分钟），保证调度端读取到干净数据。
     * 保存后下一轮调度(每分钟一次)即按新配置生效，无需重启。
     */
    @Transactional
    public void saveAreaSchedules(String json) {
        String v = json == null ? "" : json.trim();
        if (v.isEmpty()) {
            persistConfig(KEY_AREA_SCHEDULES, "", "分区域定时打印配置(JSON)");
            return;
        }
        List<AreaSchedule> parsed;
        try {
            List<Map<String, Object>> raw = objectMapper.readValue(v, new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});
            parsed = new ArrayList<>();
            for (Map<String, Object> item : raw) {
                if (item == null) continue;
                String area = item.get("area") == null ? null : String.valueOf(item.get("area")).trim();
                if (area == null || area.isEmpty()) throw new IllegalArgumentException("每个区域必须填写 area");
                AreaSchedule s = new AreaSchedule();
                s.area = area;
                s.enabled = item.get("enabled") == null || Boolean.parseBoolean(String.valueOf(item.get("enabled")));
                s.intervalMinutes = parseLong(item.get("intervalMinutes"), 60);
                s.delayMinutes = parseDelay(item.get("delayMinutes"));
                s.printer = trimToNull(item.get("printer") == null ? null : String.valueOf(item.get("printer")));
                s.printType = trimToNull(item.get("printType") == null ? null : String.valueOf(item.get("printType")));
                parsed.add(s);
            }
        } catch (com.example.materialpull.common.BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new com.example.materialpull.common.BusinessException(
                    com.example.materialpull.common.ErrorCode.PARAM_ERROR, "分区域定时配置格式不正确：" + e.getMessage());
        }
        List<Map<String, Object>> normalized = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (AreaSchedule s : parsed) {
            if (!seen.add(s.area)) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("area", s.area);
            m.put("enabled", s.enabled);
            m.put("intervalMinutes", s.intervalMinutes);
            m.put("delayMinutes", s.delayMinutes < 0 ? 0 : s.delayMinutes);
            m.put("printer", s.printer == null ? "" : s.printer);
            m.put("printType", s.printType == null ? "" : s.printType);
            normalized.add(m);
        }
        try {
            persistConfig(KEY_AREA_SCHEDULES, objectMapper.writeValueAsString(normalized), "分区域定时打印配置(JSON)");
        } catch (Exception e) {
            throw new com.example.materialpull.common.BusinessException(
                    com.example.materialpull.common.ErrorCode.PARAM_ERROR, "保存分区域定时配置失败：" + e.getMessage());
        }
    }

    private void persistConfig(String key, String value, String name) {
        SystemConfigEntity cfg = configRepository.findByConfigKey(key).orElseGet(() -> {
            SystemConfigEntity c = new SystemConfigEntity();
            c.setConfigKey(key);
            c.setConfigName(name);
            c.setEditable(true);
            return c;
        });
        cfg.setConfigValue(value);
        configRepository.save(cfg);
    }

    /** 解析分区域定时配置(JSON)。解析失败或为空则返回空列表，表示走全局模式。 */
    List<AreaSchedule> areaSchedules() {
        String v = trimToNull(value(KEY_AREA_SCHEDULES));
        if (v == null) return List.of();
        try {
            List<Map<String, Object>> raw = objectMapper.readValue(v, new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});
            List<AreaSchedule> list = new ArrayList<>();
            for (Map<String, Object> item : raw) {
                if (item == null) continue;
                String area = item.get("area") == null ? null : String.valueOf(item.get("area")).trim();
                if (area == null || area.isEmpty()) continue;
                AreaSchedule s = new AreaSchedule();
                s.area = area;
                s.enabled = item.get("enabled") == null || Boolean.parseBoolean(String.valueOf(item.get("enabled")));
                s.intervalMinutes = parseLong(item.get("intervalMinutes"), 60);
                s.delayMinutes = parseDelay(item.get("delayMinutes"));
                s.printer = trimToNull(item.get("printer") == null ? null : String.valueOf(item.get("printer")));
                s.printType = trimToNull(item.get("printType") == null ? null : String.valueOf(item.get("printType")));
                list.add(s);
            }
            // 同一区域只保留第一条，避免重复触发
            Map<String, AreaSchedule> dedup = list.stream().collect(Collectors.toMap(a -> a.area, a -> a, (a, b) -> a, LinkedHashMap::new));
            return new ArrayList<>(dedup.values());
        } catch (Exception e) {
            log.warn("分区域定时打印配置解析失败，本轮退回全局模式：{}", e.getMessage());
            return List.of();
        }
    }

    private Map<String, String> readAreaLastRun() {
        String v = trimToNull(value(KEY_AREA_LAST_RUN));
        if (v == null) return new HashMap<>();
        try {
            return objectMapper.readValue(v, new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private void writeAreaLastRun(Map<String, String> map) {
        SystemConfigEntity cfg = configRepository.findByConfigKey(KEY_AREA_LAST_RUN).orElseGet(() -> {
            SystemConfigEntity c = new SystemConfigEntity();
            c.setConfigKey(KEY_AREA_LAST_RUN);
            c.setConfigName("分区域定时打印各区域上次执行时间(JSON)");
            c.setEditable(false);
            return c;
        });
        try {
            cfg.setConfigValue(objectMapper.writeValueAsString(map));
            configRepository.save(cfg);
        } catch (Exception e) {
            log.warn("写入分区域上次执行时间失败：{}", e.getMessage());
        }
    }

    private LocalDateTime parseTs(String v) {
        String s = trimToNull(v);
        if (s == null) return null;
        try {
            return LocalDateTime.parse(s, TS);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private long parseLong(Object v, long fallback) {
        if (v == null) return fallback;
        try {
            long n = Long.parseLong(String.valueOf(v).trim());
            return n < 1 ? 1 : n;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** 解析区域延时分钟：为空返回 -1(回退全局)，负数归零。 */
    private long parseDelay(Object v) {
        if (v == null) return -1;
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) return -1;
        try {
            long n = Long.parseLong(s);
            return n < 0 ? 0 : n;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private String trimToNull(String v) { return v == null || v.isBlank() ? null : v.trim(); }
    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) if (v != null && !v.isBlank()) return v.trim();
        return null;
    }

    /** 单个配送区域的定时打印设置。 */
    static class AreaSchedule {
        String area;
        boolean enabled = true;
        long intervalMinutes = 60;
        long delayMinutes = -1; // -1 表示未设置，回退全局延时
        String printer;
        String printType;
    }
}
