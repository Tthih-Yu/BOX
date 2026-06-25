package com.example.materialpull.repository;

import com.example.materialpull.entity.LabelScanRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface LabelScanRuleRepository extends JpaRepository<LabelScanRuleEntity, Long> {
    Optional<LabelScanRuleEntity> findByRuleCode(String ruleCode);
    List<LabelScanRuleEntity> findByEnabledTrueOrderByPriorityNoAscIdAsc();
}
