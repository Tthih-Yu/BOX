package com.example.materialpull.service;

import com.example.materialpull.common.BusinessException;
import com.example.materialpull.common.ErrorCode;
import com.example.materialpull.entity.SystemConfigEntity;
import com.example.materialpull.enums.UserRole;
import com.example.materialpull.repository.SystemConfigRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 按角色配置菜单可见性。以 JSON 存在 sys_config(menu.role-permissions)：
 * { "VIEWER": ["/tasks","/print-jobs","/mappings"], ... }。
 * 某角色若在此配置中有条目，则前端菜单严格按该列表显示；无条目则前端走默认角色映射。
 * 管理员始终可见全部菜单，不受此配置约束。菜单路径由前端定义，后端只做透明存取。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MenuPermissionService {
    private final SystemConfigRepository configRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    static final String KEY = "menu.role-permissions";
    static final String KEY_NAME = "各角色可见菜单配置(JSON)";
    static final String KEY_REMARK = "按角色设置可见菜单路径，格式 {\"VIEWER\":[\"/tasks\",\"/print-jobs\",\"/mappings\"]}。请在“系统管理-菜单权限”页编辑，勿手改。";

    /** 普通用户(只读角色)默认只看：仓库补货任务、出货标签打印、料号映射。 */
    static final List<String> DEFAULT_VIEWER_MENUS = List.of("/tasks", "/print-jobs", "/mappings");

    /** 读取全部角色的菜单配置(供管理员编辑)。缺省时给只读用户填入默认菜单。 */
    public Map<String, List<String>> readAll() {
        Map<String, List<String>> map = parse(rawValue());
        if (!map.containsKey(UserRole.VIEWER.name())) {
            map.put(UserRole.VIEWER.name(), new ArrayList<>(DEFAULT_VIEWER_MENUS));
        }
        return map;
    }

    /** 返回指定角色的菜单白名单；返回 null 表示该角色未配置，前端应走默认角色映射。 */
    public List<String> menusForRole(UserRole role) {
        if (role == null) return null;
        if (role == UserRole.ADMIN || role == UserRole.SYSTEM) return null;
        Map<String, List<String>> map = parse(rawValue());
        if (map.containsKey(role.name())) return map.get(role.name());
        if (role == UserRole.VIEWER) return new ArrayList<>(DEFAULT_VIEWER_MENUS);
        return null;
    }

    @Transactional
    public void saveAll(Map<String, List<String>> permissions) {
        Map<String, List<String>> normalized = new LinkedHashMap<>();
        if (permissions != null) {
            for (Map.Entry<String, List<String>> e : permissions.entrySet()) {
                String role = e.getKey() == null ? null : e.getKey().trim().toUpperCase(Locale.ROOT);
                if (role == null || role.isEmpty()) continue;
                try {
                    UserRole.valueOf(role);
                } catch (IllegalArgumentException ex) {
                    continue;
                }
                if (UserRole.ADMIN.name().equals(role) || UserRole.SYSTEM.name().equals(role)) continue;
                List<String> paths = new ArrayList<>();
                if (e.getValue() != null) {
                    for (String p : e.getValue()) {
                        String path = p == null ? null : p.trim();
                        if (path != null && !path.isEmpty() && !paths.contains(path)) paths.add(path);
                    }
                }
                normalized.put(role, paths);
            }
        }
        String json;
        try {
            json = objectMapper.writeValueAsString(normalized);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "菜单权限配置序列化失败：" + e.getMessage());
        }
        SystemConfigEntity cfg = configRepository.findByConfigKey(KEY).orElseGet(() -> {
            SystemConfigEntity c = new SystemConfigEntity();
            c.setConfigKey(KEY);
            c.setConfigName(KEY_NAME);
            c.setRemark(KEY_REMARK);
            c.setEditable(false);
            return c;
        });
        cfg.setConfigValue(json);
        configRepository.save(cfg);
    }

    private String rawValue() {
        return configRepository.findByConfigKey(KEY).map(SystemConfigEntity::getConfigValue).orElse(null);
    }

    private Map<String, List<String>> parse(String json) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        if (json == null || json.isBlank()) return result;
        try {
            Map<String, List<String>> raw = objectMapper.readValue(json,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, List<String>>>() {});
            for (Map.Entry<String, List<String>> e : raw.entrySet()) {
                if (e.getKey() == null) continue;
                result.put(e.getKey().trim().toUpperCase(Locale.ROOT), e.getValue() == null ? List.of() : e.getValue());
            }
        } catch (Exception e) {
            log.warn("菜单权限配置解析失败，按未配置处理：{}", e.getMessage());
        }
        return result;
    }
}
