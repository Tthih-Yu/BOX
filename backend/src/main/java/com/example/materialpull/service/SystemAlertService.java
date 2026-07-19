package com.example.materialpull.service;

import com.example.materialpull.common.BusinessException;
import com.example.materialpull.common.ErrorCode;
import com.example.materialpull.common.IdGenerator;
import com.example.materialpull.entity.SystemAlertEntity;
import com.example.materialpull.repository.SystemAlertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SystemAlertService {
    private final SystemAlertRepository repository;

    public SystemAlertEntity open(String level, String category, String businessNo, String title, String content) {
        String safeBusinessNo = businessNo == null ? "UNKNOWN" : businessNo;
        var existing = repository.findFirstByStatusAndCategoryAndBusinessNoAndTitleOrderByCreatedAtDesc("OPEN", category, safeBusinessNo, title);
        if (existing.isPresent()) {
            SystemAlertEntity e = existing.get();
            e.setLevel(level);
            e.setContent(content);
            return repository.save(e);
        }
        SystemAlertEntity e = new SystemAlertEntity();
        e.setAlertNo(IdGenerator.id("AL"));
        e.setLevel(level);
        e.setCategory(category);
        e.setBusinessNo(safeBusinessNo);
        e.setTitle(title);
        e.setContent(content);
        return repository.save(e);
    }

    public List<SystemAlertEntity> openAlerts() { return repository.findTop100ByStatusOrderByCreatedAtDesc("OPEN"); }
    public long openCount() { return repository.countByStatus("OPEN"); }

    public SystemAlertEntity close(Long id, String operator, String remark) {
        SystemAlertEntity e = repository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "告警不存在：" + id));
        e.setOwner(operator);
        e.setCloseRemark(remark);
        e.setStatus("CLOSED");
        e.setClosedAt(LocalDateTime.now());
        return repository.save(e);
    }
}
