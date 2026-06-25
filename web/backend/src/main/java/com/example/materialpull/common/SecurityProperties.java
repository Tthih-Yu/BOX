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
}
