package com.example.materialpull.service.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.materialpull.service.realtime.RedisMessageRelay.Envelope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Redis 广播订阅者（集群模式）。收到其它实例（含自身）发布的消息后，
 * 向本实例已连接的 STOMP 客户端转发，从而实现跨实例实时推送。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.cluster", name = "enabled", havingValue = "true")
public class RedisBroadcastSubscriber implements MessageListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper mapper;

    public RedisBroadcastSubscriber(SimpMessagingTemplate messagingTemplate, ObjectMapper redisObjectMapper) {
        this.messagingTemplate = messagingTemplate;
        this.mapper = redisObjectMapper;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String json = new String(message.getBody(), StandardCharsets.UTF_8);
            Envelope envelope = mapper.readValue(json, Envelope.class);
            // bodyJson 是已序列化的 JSON，反序列化为通用结构后转发，保留字段供前端消费
            Object body = mapper.readValue(envelope.bodyJson(), Object.class);
            for (String destination : ScopeDestinations.forMessage(envelope.topic(), new RelayScope(
                    envelope.factory(), envelope.deliveryArea()))) {
                messagingTemplate.convertAndSend(destination, body);
            }
        } catch (Exception e) {
            log.warn("Redis 广播转发失败：{}", e.getMessage());
        }
    }

    private record RelayScope(String factory, String deliveryArea) {
        public String getFactory() { return factory; }
        public String getDeliveryArea() { return deliveryArea; }
    }
}
