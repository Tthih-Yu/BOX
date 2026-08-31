package com.example.materialpull.repository;

import com.example.materialpull.entity.DeliveryAreaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeliveryAreaRepository extends JpaRepository<DeliveryAreaEntity, Long> {

    /**
     * 查询工厂的所有启用的配送区域
     */
    List<DeliveryAreaEntity> findByFactoryCodeAndEnabledTrueOrderByDisplayOrderAscAreaCodeAsc(String factoryCode);

    /**
     * 查询工厂的所有配送区域（包含禁用）
     */
    List<DeliveryAreaEntity> findByFactoryCodeOrderByDisplayOrderAscAreaCodeAsc(String factoryCode);

    /**
     * 根据工厂代码和区域代码查询
     */
    Optional<DeliveryAreaEntity> findByFactoryCodeAndAreaCode(String factoryCode, String areaCode);

    /**
     * 检查区域是否存在
     */
    boolean existsByFactoryCodeAndAreaCode(String factoryCode, String areaCode);

    /**
     * 批量查询指定区域
     */
    @Query("SELECT d FROM DeliveryAreaEntity d WHERE d.factoryCode = :factoryCode AND d.areaCode IN :areaCodes")
    List<DeliveryAreaEntity> findByFactoryCodeAndAreaCodeIn(String factoryCode, List<String> areaCodes);

    /**
     * 查询所有启用的区域（跨工厂）
     */
    List<DeliveryAreaEntity> findByEnabledTrueOrderByFactoryCodeAscDisplayOrderAscAreaCodeAsc();
}
