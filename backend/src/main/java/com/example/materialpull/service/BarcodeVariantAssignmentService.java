package com.example.materialpull.service;

import com.example.materialpull.entity.ReplenishmentTaskEntity;
import com.example.materialpull.repository.ReplenishmentTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 为同一六位仓库代号串行分配 CODE_128 变体序号。 */
@Service
@RequiredArgsConstructor
public class BarcodeVariantAssignmentService {
    private final ReplenishmentTaskRepository taskRepository;

    /**
     * 调用方必须处于建单事务中。悲观锁会锁住该仓库代号的最后一个编号，
     * 数据库唯一索引再提供最终防线，避免并发建单分到相同序号。
     * 序号永久递增，渲染时按变体池容量取模，因此变体池用完后可以安全循环。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void assignIfNeeded(ReplenishmentTaskEntity task) {
        if (task == null || task.getBarcodeVariantNo() != null) return;
        String warehouseCode = task.getWarehouseCode() == null ? null : task.getWarehouseCode().trim();
        if (!Code128VariantEncoder.isSixDigit(warehouseCode)) return;

        int next = taskRepository
                .findFirstByWarehouseCodeAndBarcodeVariantNoIsNotNullOrderByBarcodeVariantNoDesc(warehouseCode)
                .map(existing -> existing.getBarcodeVariantNo() + 1)
                .orElse(0);
        task.setBarcodeVariantNo(next);
    }
}
