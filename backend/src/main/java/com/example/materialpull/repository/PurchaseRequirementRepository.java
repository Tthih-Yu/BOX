package com.example.materialpull.repository;

import com.example.materialpull.entity.PurchaseRequirementEntity;
import com.example.materialpull.enums.PurchaseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface PurchaseRequirementRepository extends JpaRepository<PurchaseRequirementEntity, Long> {
    Optional<PurchaseRequirementEntity> findByPurchaseNo(String purchaseNo);
    List<PurchaseRequirementEntity> findByDemandNo(String demandNo);
    List<PurchaseRequirementEntity> findByStatus(PurchaseStatus status);
}
