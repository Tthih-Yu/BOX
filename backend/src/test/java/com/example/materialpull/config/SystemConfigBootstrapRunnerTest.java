package com.example.materialpull.config;

import com.example.materialpull.entity.SystemConfigEntity;
import com.example.materialpull.repository.SystemConfigRepository;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SystemConfigBootstrapRunnerTest {

    @Test
    void consolidatesThreeExistingKeysAndPreservesDisabledState() {
        Map<String, SystemConfigEntity> configs = new LinkedHashMap<>();
        configs.put("task.dedup.enabled", config("task.dedup.enabled", "false"));
        configs.put("task.dedup.window-seconds", config("task.dedup.window-seconds", "60"));
        configs.put("task.dedup.window-minutes", config("task.dedup.window-minutes", "1"));

        runWith(configs);

        assertEquals("0", configs.get("task.dedup.window-seconds").getConfigValue());
        assertEquals("补货任务去重时间窗(秒，0=关闭)",
                configs.get("task.dedup.window-seconds").getConfigName());
        assertFalse(configs.containsKey("task.dedup.enabled"));
        assertFalse(configs.containsKey("task.dedup.window-minutes"));
    }

    @Test
    void preservesEnabledSecondsAndRemovesLegacyKeys() {
        Map<String, SystemConfigEntity> configs = new LinkedHashMap<>();
        configs.put("task.dedup.enabled", config("task.dedup.enabled", "true"));
        configs.put("task.dedup.window-seconds", config("task.dedup.window-seconds", "65"));
        configs.put("task.dedup.window-minutes", config("task.dedup.window-minutes", "3"));

        runWith(configs);

        assertEquals("65", configs.get("task.dedup.window-seconds").getConfigValue());
        assertFalse(configs.containsKey("task.dedup.enabled"));
        assertFalse(configs.containsKey("task.dedup.window-minutes"));
    }

    @Test
    void enabledSwitchFallsBackToLegacyMinutesWhenSecondsAreZero() {
        Map<String, SystemConfigEntity> configs = new LinkedHashMap<>();
        configs.put("task.dedup.enabled", config("task.dedup.enabled", "true"));
        configs.put("task.dedup.window-seconds", config("task.dedup.window-seconds", "0"));
        configs.put("task.dedup.window-minutes", config("task.dedup.window-minutes", "2"));

        runWith(configs);

        assertEquals("120", configs.get("task.dedup.window-seconds").getConfigValue());
        assertFalse(configs.containsKey("task.dedup.enabled"));
        assertFalse(configs.containsKey("task.dedup.window-minutes"));
    }

    @Test
    void convertsLegacyMinutesWhenItIsTheOnlyExistingKey() {
        Map<String, SystemConfigEntity> configs = new LinkedHashMap<>();
        configs.put("task.dedup.window-minutes", config("task.dedup.window-minutes", "3"));

        runWith(configs);

        assertEquals("180", configs.get("task.dedup.window-seconds").getConfigValue());
        assertFalse(configs.containsKey("task.dedup.window-minutes"));
    }

    private void runWith(Map<String, SystemConfigEntity> configs) {
        SystemConfigRepository repository = mock(SystemConfigRepository.class);
        when(repository.findByConfigKey(anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(configs.get(invocation.getArgument(0))));
        when(repository.save(any(SystemConfigEntity.class))).thenAnswer(invocation -> {
            SystemConfigEntity saved = invocation.getArgument(0);
            configs.put(saved.getConfigKey(), saved);
            return saved;
        });
        org.mockito.Mockito.doAnswer(invocation -> {
            SystemConfigEntity deleted = invocation.getArgument(0);
            configs.remove(deleted.getConfigKey());
            return null;
        }).when(repository).delete(any(SystemConfigEntity.class));

        new SystemConfigBootstrapRunner(repository).run();
    }

    private SystemConfigEntity config(String key, String value) {
        SystemConfigEntity entity = new SystemConfigEntity();
        entity.setConfigKey(key);
        entity.setConfigValue(value);
        entity.setEditable(true);
        return entity;
    }
}
