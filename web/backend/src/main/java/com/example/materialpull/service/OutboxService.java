package com.example.materialpull.service;

import com.example.materialpull.common.IdGenerator;
import com.example.materialpull.entity.OutboxEventEntity;
import com.example.materialpull.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class OutboxService {
    private final OutboxEventRepository repository;

    public void record(String topic, String businessNo, Object payload) {
        try {
            OutboxEventEntity e = new OutboxEventEntity();
            e.setEventNo(IdGenerator.id("EV"));
            e.setTopic(topic);
            e.setBusinessNo(businessNo);
            e.setPayload(String.valueOf(payload));
            e.setNextRetryAt(LocalDateTime.now());
            repository.save(e);
        } catch (Exception ignored) {
            // Outbox 不能反向拖垮主业务，失败由接口日志和任务日志兜底记录。
        }
    }
}
