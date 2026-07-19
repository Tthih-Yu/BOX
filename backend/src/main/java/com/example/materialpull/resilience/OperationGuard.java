package com.example.materialpull.resilience;

import com.example.materialpull.common.BusinessException;
import com.example.materialpull.common.ErrorCode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class OperationGuard {
    public String notBlank(String value, String name) {
        if (value == null || value.trim().isEmpty()) throw new BusinessException(ErrorCode.PARAM_ERROR, name + "不能为空");
        return value.trim();
    }
    public BigDecimal positive(BigDecimal value, String name) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) throw new BusinessException(ErrorCode.PARAM_ERROR, name + "必须大于0");
        return value;
    }
    public void isTrue(boolean condition, ErrorCode code, String message) {
        if (!condition) throw new BusinessException(code, message);
    }
}
