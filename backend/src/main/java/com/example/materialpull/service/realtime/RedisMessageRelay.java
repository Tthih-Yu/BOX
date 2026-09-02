package com.example.materialpull.service.realtime;

import com.example.materialpull.config.RedisConfig.ClusterKeyspace;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis 中继（集群模式）。把消息封装后发布到 Redis 频道，
 * 各实例的订阅者收到后再向本地 STOMP 客户端广播，实现跨实例推送。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.cluster", name = "enabled", havingValue = "true")
public class RedisMessageRelay implements MessageRelay {

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final ClusterKeyspace keys;

    public RedisMessageRelay(StringRedisTemplate redis, ObjectMapper redisObjectMapper, ClusterKeyspace keys) {
        this.redis = redis;
        this.mapper = redisObjectMapper;
        this.keys = keys;
    }

    public String channel() {
        return keys.of("ws-broadcast");
    }

    @Override
    public void relay(String topic, Object body) {
        try {
            String payload = mapper.writeValueAsString(new Envelope(topic, mapper.writeValueAsString(body),
                    ScopeDestinations.scopeType(body), ScopeDestinations.factory(body), ScopeDestinations.area(body)));
            redis.convertAndSend(channel(), payload);
        } catch (Exception e) {
            log.warn("Redis 广播发布失败 topic={} msg={}", topic, e.getMessage());
        }
    }

    /** body 预先序列化为 JSON 字符串，订阅端按目标类型或原样转发。 */
    public record Envelope(String topic, String bodyJson, String scopeType, String factory, String deliveryArea) {}
}
