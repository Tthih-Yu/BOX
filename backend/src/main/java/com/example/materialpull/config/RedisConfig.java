package com.example.materialpull.config;

import com.example.materialpull.common.ClusterProperties;
import com.example.materialpull.service.realtime.RedisBroadcastSubscriber;
import com.example.materialpull.service.realtime.RedisMessageRelay;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * 仅在集群模式启用时装配 Redis 相关 Bean。
 * 关闭集群（默认）时不创建这些 Bean，因此单实例部署无需连接 Redis。
 */
@Configuration
@ConditionalOnProperty(prefix = "app.cluster", name = "enabled", havingValue = "true")
public class RedisConfig {

    /** 复用 Spring 容器内的 ObjectMapper 配置并补充时间模块，统一序列化策略。 */
    @Bean
    public ObjectMapper redisObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .findAndRegisterModules();
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }

    @Bean
    public ClusterKeyspace clusterKeyspace(ClusterProperties properties) {
        return new ClusterKeyspace(properties.getKeyPrefix());
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory factory,
            RedisBroadcastSubscriber subscriber,
            RedisMessageRelay relay) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        container.addMessageListener(subscriber, new ChannelTopic(relay.channel()));
        return container;
    }

    /** 统一拼接 Redis key，避免多环境共用一个 Redis 时互相覆盖。 */
    public record ClusterKeyspace(String prefix) {
        public String of(String... parts) {
            StringBuilder sb = new StringBuilder(prefix);
            for (String p : parts) sb.append(':').append(p);
            return sb.toString();
        }
    }
}
