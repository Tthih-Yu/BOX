package com.example.materialpull.service;

import com.example.materialpull.common.RequestContext;
import com.example.materialpull.entity.UserScopeAuditLogEntity;
import com.example.materialpull.entity.UserEntity;
import com.example.materialpull.repository.UserScopeAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

/** 写入账号安全属性审计，不记录任何明文密码或密码哈希。 */
@Service
@RequiredArgsConstructor
public class UserScopeAuditService {
    private final UserScopeAuditLogRepository repository;

    public void record(String action, UserSnapshot before, UserEntity after, Collection<String> afterAreas, String reason) {
        UserScopeAuditLogEntity log = new UserScopeAuditLogEntity();
        log.setAction(action);
        log.setTargetUserId(after.getId());
        log.setTargetUsername(after.getUsername());
        log.setOperatorUserId(RequestContext.getUserId());
        log.setOperatorUsername(RequestContext.getUsername());
        log.setOldRole(before == null ? null : before.role());
        log.setNewRole(after.getRole() == null ? null : after.getRole().name());
        log.setOldFactory(before == null ? null : before.factory());
        log.setNewFactory(after.getFactory());
        log.setOldDeliveryAreas(before == null ? null : areas(before.deliveryAreas()));
        log.setNewDeliveryAreas(areas(afterAreas));
        log.setOldEnabled(before == null ? null : before.enabled());
        log.setNewEnabled(after.getEnabled());
        log.setReason(trim(reason));
        log.setTraceId(RequestContext.getTraceId());
        repository.save(log);
    }

    public UserSnapshot snapshot(UserEntity user, Collection<String> areas) {
        return new UserSnapshot(user.getId(), user.getUsername(), user.getRole() == null ? null : user.getRole().name(),
                user.getFactory(), areas == null ? List.of() : List.copyOf(areas), user.getEnabled());
    }

    public void recordDeletion(UserSnapshot before, String reason) {
        UserScopeAuditLogEntity log = new UserScopeAuditLogEntity();
        log.setAction("DELETE");
        log.setTargetUserId(before.userId());
        log.setTargetUsername(before.username());
        log.setOperatorUserId(RequestContext.getUserId());
        log.setOperatorUsername(RequestContext.getUsername());
        log.setOldRole(before.role());
        log.setOldFactory(before.factory());
        log.setOldDeliveryAreas(areas(before.deliveryAreas()));
        log.setOldEnabled(before.enabled());
        log.setReason(trim(reason));
        log.setTraceId(RequestContext.getTraceId());
        repository.save(log);
    }

    private String areas(Collection<String> areas) {
        return areas == null || areas.isEmpty() ? null : String.join(",", areas.stream().filter(x -> x != null && !x.isBlank()).sorted().toList());
    }
    private String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    public record UserSnapshot(Long userId, String username, String role, String factory, List<String> deliveryAreas, Boolean enabled) {}
}
