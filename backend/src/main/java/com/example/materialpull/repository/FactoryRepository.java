package com.example.materialpull.repository;

import com.example.materialpull.entity.FactoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FactoryRepository extends JpaRepository<FactoryEntity, Long> {

    /**
     * 根据工厂代码查询
     */
    Optional<FactoryEntity> findByFactoryCode(String factoryCode);

    /**
     * 查询所有启用的工厂
     */
    List<FactoryEntity> findByEnabledTrueOrderByDisplayOrderAscFactoryCodeAsc();

    /**
     * 查询所有工厂（包含禁用）
     */
    List<FactoryEntity> findAllByOrderByDisplayOrderAscFactoryCodeAsc();

    /**
     * 检查工厂代码是否存在
     */
    boolean existsByFactoryCode(String factoryCode);
}
