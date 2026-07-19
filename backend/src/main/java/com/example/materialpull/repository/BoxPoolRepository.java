package com.example.materialpull.repository;

import com.example.materialpull.entity.BoxPoolEntity;
import com.example.materialpull.enums.BoxPoolStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface BoxPoolRepository extends JpaRepository<BoxPoolEntity, Long> {
    Optional<BoxPoolEntity> findByContainerNo(String containerNo);
    List<BoxPoolEntity> findTop1000ByOrderByUpdatedAtDesc();
    List<BoxPoolEntity> findTop1000ByStatusOrderByUpdatedAtDesc(BoxPoolStatus status);
    List<BoxPoolEntity> findByStatus(BoxPoolStatus status);
    List<BoxPoolEntity> findByTaskNo(String taskNo);
    long countByStatus(BoxPoolStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from BoxPoolEntity b where b.containerNo = :containerNo")
    Optional<BoxPoolEntity> findByContainerNoForUpdate(@Param("containerNo") String containerNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from BoxPoolEntity b where b.status = :status and (:boxSize is null or b.boxSize = :boxSize) order by b.updatedAt asc")
    List<BoxPoolEntity> findCandidatesForUpdate(@Param("status") BoxPoolStatus status, @Param("boxSize") String boxSize);
}
