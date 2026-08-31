package com.example.materialpull.service;

import com.example.materialpull.common.BusinessException;
import com.example.materialpull.common.ErrorCode;
import com.example.materialpull.security.SessionUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * DataScope 权限控制服务
 * 核心职责：判断当前用户是否可以访问指定范围的数据
 */
@Slf4j
@Service
public class DataScopeService {

    /**
     * 从当前请求上下文获取用户信息
     * TODO: 需要从 RequestContext 或 SecurityContext 获取
     */
    private SessionUser getCurrentUser() {
        // 临时实现，后续需要从认证链获取
        throw new UnsupportedOperationException("需要实现认证链后才能获取当前用户");
    }

    /**
     * 判断当前用户是否为全局管理员
     */
    public boolean isGlobalAdmin() {
        SessionUser user = getCurrentUser();
        // ADMIN 角色且 factory 为 NULL 表示全局管理员
        return "ADMIN".equals(user.getRole()) && user.getFactory() == null;
    }

    /**
     * 获取当前用户的工厂
     * 全局管理员返回 null
     */
    public String currentFactory() {
        if (isGlobalAdmin()) {
            return null;
        }
        SessionUser user = getCurrentUser();
        if (user.getFactory() == null || user.getFactory().isBlank()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号未配置工厂范围");
        }
        return user.getFactory().trim();
    }

    /**
     * 获取当前用户的配送区域列表
     * 全局管理员返回空列表
     */
    public List<String> currentDeliveryAreas() {
        if (isGlobalAdmin()) {
            return List.of();
        }
        SessionUser user = getCurrentUser();
        if (user.getDeliveryAreas() == null || user.getDeliveryAreas().isEmpty()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号未配置配送区域");
        }
        return user.getDeliveryAreas();
    }

    /**
     * 判断当前用户是否可以访问指定的工厂+区域数据 (FACTORY_AREA)
     * 
     * @param factory 数据的工厂
     * @param deliveryArea 数据的配送区域
     * @return true 表示可以访问
     */
    public boolean canAccessFactoryArea(String factory, String deliveryArea) {
        if (isGlobalAdmin()) {
            return true;
        }

        // 数据范围缺失，拒绝访问
        if (factory == null || factory.isBlank() || deliveryArea == null || deliveryArea.isBlank()) {
            log.warn("数据范围缺失，拒绝访问: factory={}, deliveryArea={}", factory, deliveryArea);
            return false;
        }

        String userFactory = currentFactory();
        List<String> userAreas = currentDeliveryAreas();

        // 工厂必须匹配
        if (!userFactory.equalsIgnoreCase(factory.trim())) {
            return false;
        }

        // 配送区域必须在用户的范围内
        String normalizedArea = deliveryArea.trim();
        return userAreas.stream()
                .anyMatch(area -> area.equalsIgnoreCase(normalizedArea));
    }

    /**
     * 判断当前用户是否可以访问指定的工厂数据 (FACTORY_ONLY)
     * 
     * @param factory 数据的工厂
     * @return true 表示可以访问
     */
    public boolean canAccessFactoryOnly(String factory) {
        if (isGlobalAdmin()) {
            return true;
        }

        // 数据工厂缺失，拒绝访问
        if (factory == null || factory.isBlank()) {
            log.warn("数据工厂缺失，拒绝访问: factory={}", factory);
            return false;
        }

        String userFactory = currentFactory();
        return userFactory.equalsIgnoreCase(factory.trim());
    }

    /**
     * 判断当前用户是否可以访问全局数据 (GLOBAL)
     * 
     * @return true 表示可以访问
     */
    public boolean canAccessGlobal() {
        // 全局数据所有人都可以访问（如 SimpleBOM）
        // 但写操作可能需要额外的功能权限
        return true;
    }

    /**
     * 要求当前用户可以访问指定的工厂+区域数据，否则抛出异常
     * 
     * @param factory 数据的工厂
     * @param deliveryArea 数据的配送区域
     * @throws BusinessException 如果无权访问
     */
    public void requireAccessFactoryArea(String factory, String deliveryArea) {
        if (!canAccessFactoryArea(factory, deliveryArea)) {
            log.warn("越权访问被拒绝 [FACTORY_AREA]: factory={}, deliveryArea={}, user={}",
                    factory, deliveryArea, getCurrentUser().getUsername());
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该数据");
        }
    }

    /**
     * 要求当前用户可以访问指定的工厂数据，否则抛出异常
     * 
     * @param factory 数据的工厂
     * @throws BusinessException 如果无权访问
     */
    public void requireAccessFactoryOnly(String factory) {
        if (!canAccessFactoryOnly(factory)) {
            log.warn("越权访问被拒绝 [FACTORY_ONLY]: factory={}, user={}",
                    factory, getCurrentUser().getUsername());
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该数据");
        }
    }

    /**
     * 判断对象是否有未归属的范围（用于历史数据处理）
     * 
     * @param factory 数据的工厂
     * @param deliveryArea 数据的配送区域（可选）
     * @return true 表示未归属
     */
    public boolean isUnassigned(String factory, String deliveryArea) {
        if (factory == null || factory.isBlank()) {
            return true;
        }
        // 对于 FACTORY_AREA 类型，deliveryArea 也必须存在
        if (deliveryArea != null && deliveryArea.isBlank()) {
            return true;
        }
        return false;
    }

    /**
     * 判断当前用户是否可以访问未归属的数据
     * 只有全局管理员可以访问
     */
    public boolean canAccessUnassigned() {
        return isGlobalAdmin();
    }

    /**
     * 为批量操作验证所有项目的访问权限
     * 
     * @param items 项目列表
     * @param factoryExtractor 提取工厂的函数
     * @param areaExtractor 提取配送区域的函数
     * @throws BusinessException 如果有任何一项无权访问
     */
    public <T> void requireAccessBatch(List<T> items,
                                       java.util.function.Function<T, String> factoryExtractor,
                                       java.util.function.Function<T, String> areaExtractor) {
        for (T item : items) {
            String factory = factoryExtractor.apply(item);
            String area = areaExtractor.apply(item);
            if (area != null) {
                requireAccessFactoryArea(factory, area);
            } else {
                requireAccessFactoryOnly(factory);
            }
        }
    }
}
