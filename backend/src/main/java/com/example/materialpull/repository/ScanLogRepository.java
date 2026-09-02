package com.example.materialpull.repository;

import com.example.materialpull.entity.ScanLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.*;

public interface ScanLogRepository extends JpaRepository<ScanLogEntity, Long>, JpaSpecificationExecutor<ScanLogEntity> {
    List<ScanLogEntity> findTop1000ByOrderByScanAtDesc();
    List<ScanLogEntity> findTop100ByLabelCodeOrderByScanAtDesc(String labelCode);
    List<ScanLogEntity> findTop1000ByFactoryIgnoreCaseAndDeliveryAreaInOrderByScanAtDesc(
            String factory, Collection<String> deliveryAreas);
    List<ScanLogEntity> findTop100ByFactoryIgnoreCaseAndDeliveryAreaInAndLabelCodeOrderByScanAtDesc(
            String factory, Collection<String> deliveryAreas, String labelCode);
}
