package com.example.materialpull.common;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.security")
public class SecurityProperties {
    /** 普通登录会话有效期，单位分钟。 */
    private long sessionTtlMinutes = 480;
    /** WebSocket 一次性握手票据有效期，单位秒。 */
    private long websocketTicketTtlSeconds = 120;
    /** 外部系统回调 / 集成接口使用的机器密钥。生产环境必须通过环境变量覆盖。 */
    private String externalApiKey = "CHANGE_ME_EXTERNAL_API_KEY";
    /** 是否开放 H2 Console、Swagger 等开发工具入口。生产环境必须为 false。 */
    private boolean devToolsEnabled = false;
    /** 兼容旧版 WebSocket 查询参数 token。默认关闭，只允许短期 ticket。 */
    private boolean allowWebsocketQueryToken = false;

    /** 扫码设备（/scan/*）免登录鉴权加固配置。 */
    private DeviceAuth deviceAuth = new DeviceAuth();

    /** Actuator 敏感端点（prometheus/metrics/info 等）应用层防护配置。health 探针始终放行。 */
    private Actuator actuator = new Actuator();

    @Data
    public static class Actuator {
        /**
         * 访问敏感 Actuator 端点所需的管理令牌。配置后，请求需携带 X-Actuator-Token（或 Bearer）匹配才放行。
         * 为空时退回按来源 IP 白名单判断（见 allowedIpCidrs），兼容现有仅靠网络层限制的部署。
         */
        private String token = "";
        /**
         * 允许直接访问敏感 Actuator 端点的来源 IP / CIDR（按真实 remoteAddr 判断，不信任 X-Forwarded-For）。
         * 默认放行回环与内网私有网段：容器经 nginx 反代来自内网网段，Prometheus 内网抓取不受影响；
         * 公网直连(若误暴露 8080)将被拒绝。
         */
        private java.util.List<String> allowedIpCidrs = new java.util.ArrayList<>(java.util.List.of(
                "127.0.0.1/32", "::1/128", "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16"
        ));
    }

    @Data
    public static class DeviceAuth {
        /**
         * 是否要求扫码设备携带设备密钥（X-Device-Key）。
         * 默认 false 以兼容现有现场设备；生产建议开启并配合网络隔离。
         */
        private boolean requireDeviceKey = false;
        /** 允许的设备密钥（可配多个，逗号分隔由 Spring 自动绑定为 List）。 */
        private java.util.List<String> deviceKeys = new java.util.ArrayList<>();
        /**
         * 允许访问 /scan/* 的来源 IP / CIDR 白名单（逗号分隔）。
         * 为空表示不做 IP 限制；配置后仅白名单内地址可提交扫码业务。
         */
        private java.util.List<String> allowedIpCidrs = new java.util.ArrayList<>();
    }
}
