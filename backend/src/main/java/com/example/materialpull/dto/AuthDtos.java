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
    }

    public static class WsTicketResult {
        public String ticket;
        public LocalDateTime expiresAt;
    }
}
