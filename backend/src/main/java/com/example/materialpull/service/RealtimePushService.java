package com.example.materialpull.service;

import com.example.materialpull.common.AppProperties;
import com.example.materialpull.service.realtime.MessageRelay;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RealtimePushService {
    private final MessageRelay messageRelay;
    private final AppProperties properties;
    private final OutboxService outboxService;

    public void publish(String topic, Object body) {
        try {
            messageRelay.relay(topic, body);
            outboxService.record(topic, extractNo(body), body);
        } catch (Exception e) {
            log.warn("websocket push failed topic={} msg={}", topic, e.getMessage());
            outboxService.record(topic, extractNo(body), "PUSH_FAILED:" + e.getMessage());
            if (!properties.isIgnorePushFailure()) throw new RuntimeException(e);
        }
    }

    private String extractNo(Object body) {
        if (body == null) return null;
        try {
            for (String method : new String[]{"getTaskNo", "getBoxCode", "getLabelCode"}) {
                try {
                    Object v = body.getClass().getMethod(method).invoke(body);
                    if (v != null) return String.valueOf(v);
                } catch (NoSuchMethodException ignored) {}
            }
        } catch (Exception ignored) {}
        return body.getClass().getSimpleName();
    }
}
