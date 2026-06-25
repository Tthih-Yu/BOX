package com.example.materialpull.config;

import com.example.materialpull.entity.UserEntity;
import com.example.materialpull.enums.UserRole;
import com.example.materialpull.repository.UserRepository;
import com.example.materialpull.service.PasswordService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Order(0)
@RequiredArgsConstructor
public class BootstrapAdminRunner implements CommandLineRunner {
    private final UserRepository userRepository;
    private final PasswordService passwordService;

    @Value("${app.bootstrap.admin-username:admin}")
    private String adminUsername;
    @Value("${app.bootstrap.admin-password:}")
    private String adminPassword;
    @Value("${app.bootstrap.admin-real-name:系统管理员}")
    private String adminRealName;

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) return;
        if (adminPassword == null || adminPassword.isBlank()) return;
        try {
            passwordService.validateNewPassword(adminPassword);
        } catch (RuntimeException e) {
            throw new IllegalStateException("首次启动管理员密码不符合生产密码策略：" + e.getMessage());
        }
        UserEntity admin = new UserEntity();
        admin.setUsername(blankToDefault(adminUsername, "admin"));
        admin.setRealName(blankToDefault(adminRealName, "系统管理员"));
        admin.setPasswordHash(passwordService.hash(adminPassword.trim()));
        admin.setPasswordUpdatedAt(LocalDateTime.now());
        admin.setRole(UserRole.ADMIN);
        admin.setEnabled(true);
        userRepository.save(admin);
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
