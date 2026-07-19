package com.example.materialpull.config;

import com.example.materialpull.common.ClusterProperties;
import com.example.materialpull.common.SecurityProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 生产环境启动安全校验：检测到默认密钥 / 弱配置时直接抛异常阻止启动，
 * 避免「忘了改默认值就上线」。仅在非 dev 环境（生产）强校验；dev 环境只告警。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StartupSecurityGuard implements ApplicationListener<ApplicationReadyEvent> {

    private static final String DEFAULT_EXTERNAL_API_KEY = "CHANGE_ME_EXTERNAL_API_KEY";

    private final SecurityProperties securityProperties;
    private final ClusterProperties clusterProperties;
    private final Environment environment;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        boolean isDev = Arrays.asList(environment.getActiveProfiles()).contains("dev");
        List<String> problems = new ArrayList<>();

        String apiKey = securityProperties.getExternalApiKey();
        if (apiKey == null || apiKey.isBlank() || DEFAULT_EXTERNAL_API_KEY.equals(apiKey)) {
            problems.add("app.security.external-api-key 仍为默认值或为空，请通过环境变量 MATERIAL_PULL_EXTERNAL_API_KEY 设置强随机值");
        } else if (apiKey.length() < 16) {
            problems.add("app.security.external-api-key 长度过短（建议≥16位随机字符）");
        }

        if (securityProperties.isDevToolsEnabled()) {
            problems.add("app.security.dev-tools-enabled=true 会暴露 H2 控制台 / Swagger，生产必须为 false");
        }
        if (securityProperties.isAllowWebsocketQueryToken()) {
            problems.add("app.security.allow-websocket-query-token=true 允许 URL 携带 token，生产建议关闭");
        }

        if (clusterProperties.isEnabled()) {
            String redisPwd = environment.getProperty("spring.data.redis.password", "");
            if (redisPwd == null || redisPwd.isBlank()) {
                problems.add("集群模式已启用但 Redis 未设置密码，存在会话被窃取风险");
            }
        }

        if (problems.isEmpty()) {
            log.info("启动安全校验通过");
            return;
        }

        StringBuilder sb = new StringBuilder("检测到以下生产安全配置问题：");
        for (String p : problems) sb.append("\n  - ").append(p);

        if (isDev) {
            log.warn("{}\n（dev 环境仅告警，不阻止启动）", sb);
        } else {
            throw new IllegalStateException(sb + "\n生产环境拒绝启动。修正以上配置后重试，或临时以 dev profile 启动排查。");
        }
    }
}
