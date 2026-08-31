package com.example.materialpull.repository;

import com.example.materialpull.entity.UserDeliveryAreaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserDeliveryAreaRepository extends JpaRepository<UserDeliveryAreaEntity, Long> {

    /**
     * 查询用户的所有配送区域
     */
    List<UserDeliveryAreaEntity> findByUserId(Long userId);

    /**
     * 查询用户是否有指定配送区域
     */
    boolean existsByUserIdAndDeliveryArea(Long userId, String deliveryArea);

    /**
     * 删除用户的所有配送区域
     */
    @Modifying
    @Query("DELETE FROM UserDeliveryAreaEntity u WHERE u.userId = :userId")
    void deleteByUserId(Long userId);

    /**
     * 删除用户的指定配送区域
     */
    @Modifying
    @Query("DELETE FROM UserDeliveryAreaEntity u WHERE u.userId = :userId AND u.deliveryArea = :deliveryArea")
    void deleteByUserIdAndDeliveryArea(Long userId, String deliveryArea);

    /**
     * 批量查询多个用户的配送区域
     */
    @Query("SELECT u FROM UserDeliveryAreaEntity u WHERE u.userId IN :userIds ORDER BY u.userId, u.deliveryArea")
    List<UserDeliveryAreaEntity> findByUserIdIn(List<Long> userIds);
}
