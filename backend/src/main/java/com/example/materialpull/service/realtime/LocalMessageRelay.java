package com.example.materialpull.service.realtime;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * 本地中继（默认 / 单实例）。直接把消息发给本地 STOMP broker。
 */
@Component
@ConditionalOnProperty(prefix = "app.cluster", name = "enabled", havingValue = "false", matchIfMissing = true)
public class LocalMessageRelay implements MessageRelay {
    private final SimpMessagingTemplate messagingTemplate;

    public LocalMessageRelay(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void relay(String topic, Object body) {
        for (String destination : ScopeDestinations.forMessage(topic, body)) {
            messagingTemplate.convertAndSend(destination, body);
        }
    }
}
