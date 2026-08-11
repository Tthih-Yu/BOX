package com.example.materialpull.repository;

import com.example.materialpull.entity.WeeklyPlanRowEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WeeklyPlanRowRepository extends JpaRepository<WeeklyPlanRowEntity, Long> {
    Page<WeeklyPlanRowEntity> findByBatchNo(String batchNo, Pageable pageable);
    List<WeeklyPlanRowEntity> findByBatchNo(String batchNo);

    /**
     * 冲突检测：同一年份 + 周次 + 工厂 + 8D号 的历史计划行（不含本批次）。
     *
     * 带上工厂做隔离：弋江和三山各自的计划员即使排到同一个 8D 号，也互不算冲突、互不覆盖。
     * 工厂为空时只与同样为空的行比较（用 is null 判定，避免 = null 恒假导致漏检）。
     */
    @Query("select r from WeeklyPlanRowEntity r where r.planYear = :year and r.weekNo = :week " +
           "and r.productCode = :code and r.batchNo <> :batchNo " +
           "and ((:factory is null and r.factory is null) or r.factory = :factory)")
    List<WeeklyPlanRowEntity> findConflicts(@Param("year") Integer year, @Param("week") Integer week,
                                            @Param("code") String code, @Param("factory") String factory,
                                            @Param("batchNo") String batchNo);

    /** 取某年某周的全部计划行，供自动计算引擎按 8D 号汇总。 */
    List<WeeklyPlanRowEntity> findByPlanYearAndWeekNo(Integer planYear, Integer weekNo);

    @Modifying
    @Query("delete from WeeklyPlanRowEntity r where r.batchNo = :batchNo")
    int deleteByBatchNo(@Param("batchNo") String batchNo);
}
