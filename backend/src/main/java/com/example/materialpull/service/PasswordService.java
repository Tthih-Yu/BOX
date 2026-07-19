package com.example.materialpull.service;

import com.example.materialpull.common.BusinessException;
import com.example.materialpull.common.ErrorCode;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PasswordService {
    public static final int MIN_LENGTH = 10;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

    public void validateNewPassword(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) throw new BusinessException(ErrorCode.PARAM_ERROR, "密码不能为空");
        String value = rawPassword.trim();
        if (value.length() < MIN_LENGTH) throw new BusinessException(ErrorCode.PARAM_ERROR, "密码长度不能少于 " + MIN_LENGTH + " 位");
        boolean hasLetter = value.chars().anyMatch(Character::isLetter);
        boolean hasDigit = value.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) throw new BusinessException(ErrorCode.PARAM_ERROR, "密码必须同时包含字母和数字");
    }

    public String hash(String rawPassword) {
        validateNewPassword(rawPassword);
        return encoder.encode(rawPassword.trim());
    }

    public String hashExistingPasswordWithoutPolicyCheck(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) throw new BusinessException(ErrorCode.PARAM_ERROR, "密码不能为空");
        return encoder.encode(rawPassword.trim());
    }

    public boolean matches(String rawPassword, String storedHashOrLegacyPlainText) {
        if (rawPassword == null || storedHashOrLegacyPlainText == null || storedHashOrLegacyPlainText.isBlank()) return false;
        if (isBcrypt(storedHashOrLegacyPlainText)) return encoder.matches(rawPassword, storedHashOrLegacyPlainText);
        return rawPassword.equals(storedHashOrLegacyPlainText);
    }

    public boolean isBcrypt(String value) {
        return value != null && (value.startsWith("$2a$") || value.startsWith("$2b$") || value.startsWith("$2y$"));
    }
}
