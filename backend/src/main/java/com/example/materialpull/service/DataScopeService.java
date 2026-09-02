package com.example.materialpull.service;

import com.example.materialpull.common.BusinessException;
import com.example.materialpull.common.ErrorCode;
import com.example.materialpull.common.RequestContext;
import com.example.materialpull.enums.UserRole;
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
     */
    private com.example.materialpull.security.SessionUser getCurrentUser() {
        // 从 RequestContext 构造 SessionUser
        Long userId = RequestContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录");
        }
        
        com.example.materialpull.security.SessionUser user = new com.example.materialpull.security.SessionUser();
        user.setId(userId);
        user.setUsername(RequestContext.getUsername());
        user.setRealName(RequestContext.getRealName());
        user.setRole(RequestContext.getRole());
        user.setFactory(RequestContext.getFactory());
        user.setDeliveryAreas(RequestContext.getDeliveryAreas());
        user.setEnabled(true);
        return user;
    }

    /**
     * 判断当前用户是否为全局管理员
     */
    public boolean isGlobalAdmin() {
        com.example.materialpull.security.SessionUser user = getCurrentUser();
        // 全局 ADMIN 必须同时不绑定工厂和配送区域；畸形 Session 一律 Fail Closed。
        return UserRole.ADMIN == user.getRole()
                && (user.getFactory() == null || user.getFactory().isBlank())
                && (user.getDeliveryAreas() == null || user.getDeliveryAreas().isEmpty());
    }

    /**
     * 获取当前用户的工厂
     * 全局管理员返回 null
     */
    public String currentFactory() {
        if (isGlobalAdmin()) {
            return null;
        }
        com.example.materialpull.security.SessionUser user = getCurrentUser();
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
        com.example.materialpull.security.SessionUser user = getCurrentUser();
        // 配送区域为空表示当前工厂全部区域；工厂本身仍必须配置。
        return user.getDeliveryAreas() == null ? List.of() : user.getDeliveryAreas();
    }

    /**
     * 判断当前用户是否可以访问指定的工厂+区域数据 (FACTORY_AREA)
     * 
     * @param factory 数据的工厂
     * @param deliveryArea 数据的配送区域
     * @return true 表示可以访问
     */
    public boolean canAccessFactoryArea(String factory, String deliveryArea) {
        // 现场设备不携带账号 DataScope；仅 /scan/* 认证链可获得该标记，
        // 且服务端仍要求从 Label/Box/Mapping 唯一解析出完整归属。
        if (RequestContext.isTrustedScanner()) {
            return factory != null && !factory.isBlank()
                    && deliveryArea != null && !deliveryArea.isBlank();
        }
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

        // 配送区域为空表示当前工厂全部区域
        if (userAreas.isEmpty()) return true;

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

    /** 要求当前账号为不绑定工厂的全局 ADMIN。用于全库维护、无范围系统日志等入口。 */
    public void requireGlobalAdmin() {
        if (!isGlobalAdmin()) {
            log.warn("全局管理入口访问被拒绝: user={}, role={}, factory={}",
                    RequestContext.getUsername(), RequestContext.getRole(), RequestContext.getFactory());
            throw new BusinessException(ErrorCode.FORBIDDEN, "该操作仅限全局管理员");
        }
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
                    factory, deliveryArea, RequestContext.getUsername());
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
                    factory, RequestContext.getUsername());
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
        if (deliveryArea == null || deliveryArea.isBlank()) {
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
            requireAccessFactoryArea(factory, area);
        }
    }
}
