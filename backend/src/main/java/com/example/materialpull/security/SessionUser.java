package com.example.materialpull.security;

import com.example.materialpull.enums.UserRole;
import lombok.Data;

import java.util.List;

/**
 * Session 中的用户信息
 * 增加 DataScope 范围字段
 */
@Data
public class SessionUser {
    private Long id;
    private String username;
    private String realName;
    private UserRole role;
    
    // DataScope 范围字段（新增）
    private String factory;  // 所属工厂，NULL 表示全局管理员
    private List<String> deliveryAreas;  // 允许访问的配送区域列表
    
    // 原有字段
    private Boolean enabled;
    
    /**
     * 判断是否为全局管理员
     */
    public boolean isGlobalAdmin() {
        return role == UserRole.ADMIN && factory == null;
    }
    
    /**
     * 判断是否可以访问多个配送区域
     */
    public boolean hasMultipleAreas() {
        return deliveryAreas != null && deliveryAreas.size() > 1;
    }
}
