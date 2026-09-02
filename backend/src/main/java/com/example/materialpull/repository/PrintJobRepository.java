package com.example.materialpull.repository;

import com.example.materialpull.entity.PrintJobEntity;
import com.example.materialpull.enums.PrintJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface PrintJobRepository extends JpaRepository<PrintJobEntity, Long> {
    Optional<PrintJobEntity> findByPrintJobNo(String printJobNo);
    List<PrintJobEntity> findByTaskNo(String taskNo);
    List<PrintJobEntity> findByStatus(PrintJobStatus status);
    List<PrintJobEntity> findTop1000ByOrderByCreatedAtDesc();
    List<PrintJobEntity> findTop1000ByStatusOrderByCreatedAtDesc(PrintJobStatus status);
    Page<PrintJobEntity> findByStatusOrderByCreatedAtDesc(PrintJobStatus status, Pageable pageable);
    Page<PrintJobEntity> findByPrintChannelIgnoreCaseOrderByCreatedAtDesc(String printChannel, Pageable pageable);
    Page<PrintJobEntity> findByStatusAndPrintChannelIgnoreCaseOrderByCreatedAtDesc(PrintJobStatus status, String printChannel, Pageable pageable);
    List<PrintJobEntity> findTop50ByStatusInOrderByCreatedAtAsc(java.util.Collection<PrintJobStatus> statuses);

    @Query("""
            select p from PrintJobEntity p
             where p.factory = :factory
               and p.deliveryArea in :areas
               and (:status is null or p.status = :status)
               and (:channel = '' or upper(coalesce(p.printChannel, '')) = :channel)
             order by p.createdAt desc
            """)
    Page<PrintJobEntity> findScoped(@Param("factory") String factory,
                                    @Param("areas") Collection<String> areas,
                                    @Param("status") PrintJobStatus status,
                                    @Param("channel") String channel,
                                    Pageable pageable);

    /**
     * 范围账号读取打印任务：优先使用 PrintJob 自身快照；历史快照为空时，
     * 通过关联 Task 的已固化工厂/区域进行范围判定，避免历史打印记录全部不可见。
     */
    @Query("""
            select p from PrintJobEntity p
             left join ReplenishmentTaskEntity t on t.taskNo = p.taskNo
             where ((p.factory = :factory and p.deliveryArea in :areas)
                 or ((p.factory is null or p.deliveryArea is null)
                     and t.factory = :factory and t.deliveryArea in :areas))
               and (:status is null or p.status = :status)
               and (:channel = '' or upper(coalesce(p.printChannel, '')) = :channel)
             order by p.createdAt desc
            """)
    Page<PrintJobEntity> findScopedIncludingTaskFallback(@Param("factory") String factory,
                                                          @Param("areas") Collection<String> areas,
                                                          @Param("status") PrintJobStatus status,
                                                          @Param("channel") String channel,
                                                          Pageable pageable);

    @Query("""
            select p from PrintJobEntity p left join ReplenishmentTaskEntity t on t.taskNo = p.taskNo
             where ((p.factory = :factory) or ((p.factory is null or p.deliveryArea is null) and t.factory = :factory))
               and (:status is null or p.status = :status)
               and (:channel = '' or upper(coalesce(p.printChannel, '')) = :channel)
             order by p.createdAt desc
            """)
    Page<PrintJobEntity> findFactoryWideIncludingTaskFallback(@Param("factory") String factory,
                                                               @Param("status") PrintJobStatus status,
                                                               @Param("channel") String channel,
                                                               Pageable pageable);
}
