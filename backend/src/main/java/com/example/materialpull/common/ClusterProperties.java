package com.example.materialpull.common;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 集群 / 高可用相关开关。
 * 关闭时（默认）系统以单实例内存态运行，行为与改造前一致；
 * 开启时会话、业务锁、定时任务租约、WebSocket 广播全部走 Redis，支持多实例部署。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.cluster")
public class ClusterProperties {
    /** 是否启用集群模式（依赖 Redis）。生产多实例部署时设为 true。 */
    private boolean enabled = false;
    /** Redis key 统一前缀，便于多套环境共用一个 Redis 实例时隔离。 */
    private String keyPrefix = "material-pull";
}
