package com.example.materialpull.resilience;

import com.example.materialpull.common.BusinessException;
import com.example.materialpull.common.ErrorCode;
import com.example.materialpull.entity.IdempotencyEntity;
import com.example.materialpull.repository.IdempotencyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class IdempotencyService {
    private final IdempotencyRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void begin(String requestKey, String businessType, String businessNo, String requestHash) {
        if (requestKey == null || requestKey.isBlank()) return;
        LocalDateTime now = LocalDateTime.now();
        String key = requestKey.trim();
        IdempotencyEntity e = repository.findByRequestKey(key).orElse(null);
        if (e != null) {
            boolean notExpired = e.getExpiresAt() != null && e.getExpiresAt().isAfter(now);
            boolean samePayload = Objects.equals(nullToEmpty(e.getRequestHash()), nullToEmpty(requestHash));
            if (notExpired && !samePayload) {
                throw new BusinessException(ErrorCode.STATE_CONFLICT, "幂等键已被不同请求内容使用，请刷新页面后重试");
            }
            if (notExpired && "DONE".equals(e.getStatus())) {
                throw new BusinessException(ErrorCode.DUPLICATE_REQUEST, "重复请求已被系统拦截，原业务号：" + e.getBusinessNo());
            }
            if (notExpired && "RUNNING".equals(e.getStatus())) {
                throw new BusinessException(ErrorCode.DUPLICATE_REQUEST, "相同请求正在处理中，请勿重复点击");
            }
            e.setBusinessType(businessType);
            e.setBusinessNo(businessNo);
            e.setRequestHash(requestHash);
            e.setStatus("RUNNING");
            e.setMessage(null);
            e.setExpiresAt(now.plusHours(6));
            repository.save(e);
            return;
        }
        e = new IdempotencyEntity();
        e.setRequestKey(key);
        e.setRequestHash(requestHash);
        e.setBusinessType(businessType);
        e.setBusinessNo(businessNo);
        e.setStatus("RUNNING");
        e.setExpiresAt(now.plusHours(6));
        repository.save(e);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finish(String requestKey, String businessNo, String message) {
        if (requestKey == null || requestKey.isBlank()) return;
        repository.findByRequestKey(requestKey.trim()).ifPresent(e -> {
            e.setBusinessNo(businessNo);
            e.setStatus("DONE");
            e.setMessage(message);
            repository.save(e);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(String requestKey, String message) {
        if (requestKey == null || requestKey.isBlank()) return;
        repository.findByRequestKey(requestKey.trim()).ifPresent(e -> {
            e.setStatus("FAILED");
            e.setMessage(message);
            e.setExpiresAt(LocalDateTime.now().plusMinutes(15));
            repository.save(e);
        });
    }

    private String nullToEmpty(String value) { return value == null ? "" : value; }
}
