package com.example.materialpull.dto;

import com.example.materialpull.enums.UserRole;
import java.time.LocalDateTime;

public class AuthDtos {
    public static class LoginRequest {
        public String username;
        public String password;
    }

    public static class LoginResult {
        public String token;
        public String username;
        public String realName;
        public UserRole role;
        public String roleLabel;
        public LocalDateTime expiresAt;
        
        // DataScope 范围字段（新增）
        public String factory;  // 所属工厂，null 表示全局管理员
        public java.util.List<String> deliveryAreas;  // 允许访问的配送区域列表
    }

    public static class WsTicketResult {
        public String ticket;
        public LocalDateTime expiresAt;
    }
}
