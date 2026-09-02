package com.example.materialpull.security;

import com.example.materialpull.enums.UserRole;
import lombok.Data;

import java.util.List;

/**
 * Session 中的用户信息（扩展版）
 * 增加 DataScope 范围字段
 * 
 * 这个类扩展了 AuthTokenService.SessionUser record
 * 增加了 DataScope 所需的 factory 和 deliveryAreas
 */
@Data
public class SessionUser {
    private Long id;
    private String username;
    private String realName;
    private UserRole role;
    private Boolean enabled;
    
    // DataScope 范围字段（新增）
    private String factory;  // 所属工厂，NULL 表示全局管理员
    private List<String> deliveryAreas;  // 允许访问的配送区域列表
    
    /**
     * 判断是否为全局管理员
     */
    public boolean isGlobalAdmin() {
        return role == UserRole.ADMIN
                && (factory == null || factory.isBlank())
                && (deliveryAreas == null || deliveryAreas.isEmpty());
    }
    
    /**
     * 判断是否可以访问多个配送区域
     */
    public boolean hasMultipleAreas() {
        return deliveryAreas != null && deliveryAreas.size() > 1;
    }
    
    /**
     * 从 AuthTokenService.SessionUser record 转换
     * 必须完整复制 Session 中已经签发的范围；不能重新查询数据库，也不能丢弃范围。
     * 后者会让受限 ADMIN 在未来调用此转换方法时被误判为全局管理员。
     */
    public static SessionUser fromTokenSession(com.example.materialpull.service.AuthTokenService.SessionUser tokenSession) {
        SessionUser user = new SessionUser();
        user.setId(tokenSession.userId());
        user.setUsername(tokenSession.username());
        user.setRealName(tokenSession.realName());
        user.setRole(tokenSession.role());
        user.setEnabled(true);
        user.setFactory(tokenSession.factory());
        user.setDeliveryAreas(tokenSession.deliveryAreas() == null ? List.of() : List.copyOf(tokenSession.deliveryAreas()));
        return user;
    }
}
