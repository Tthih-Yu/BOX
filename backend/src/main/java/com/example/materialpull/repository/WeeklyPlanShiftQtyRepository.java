package com.example.materialpull.repository;

import com.example.materialpull.entity.WeeklyPlanShiftQtyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WeeklyPlanShiftQtyRepository extends JpaRepository<WeeklyPlanShiftQtyEntity, Long> {
    List<WeeklyPlanShiftQtyEntity> findByRowId(Long rowId);

    /**
     * 取某一天各 8D 号的数量合计（白班 + 夜班）。
     * 返回 [productCode, qtySum]，供联动计算按「当天用量」刷新料号映射数量。
     */
    @Query("select r.factory, r.productCode, sum(s.qty) from WeeklyPlanShiftQtyEntity s, WeeklyPlanRowEntity r " +
           "where s.rowId = r.id and s.planDate = :date group by r.factory, r.productCode")
    List<Object[]> sumByFactoryAndProductCodeOnDate(@Param("date") java.time.LocalDate date);

    @Query("select r.factory, r.productCode, sum(s.qty) from WeeklyPlanShiftQtyEntity s, WeeklyPlanRowEntity r " +
           "where s.rowId = r.id and s.planDate = :date and lower(r.factory) = lower(:factory) " +
           "group by r.factory, r.productCode")
    List<Object[]> sumByFactoryAndProductCodeOnDate(@Param("date") java.time.LocalDate date,
                                                     @Param("factory") String factory);

    /** 某一天是否有任何周计划数据。 */
    @Query("select count(s) from WeeklyPlanShiftQtyEntity s where s.planDate = :date")
    long countByPlanDate(@Param("date") java.time.LocalDate date);

    @Modifying
    @Query("delete from WeeklyPlanShiftQtyEntity s where s.batchNo = :batchNo")
    int deleteByBatchNo(@Param("batchNo") String batchNo);
}
