package com.example.materialpull.service.realtime;

/**
 * 实时消息中继抽象。
 * 单实例：直接通过本地 STOMP broker 广播给已连接客户端。
 * 集群：发布到 Redis 频道，各实例订阅后各自向本地客户端广播，实现跨实例推送。
 */
public interface MessageRelay {
    void relay(String topic, Object body);
}
