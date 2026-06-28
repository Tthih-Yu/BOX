package com.example.materialpull.dto;

import com.example.materialpull.enums.UserRole;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class BasicDataDtos {
    public static class UserRequest {
        public Long id;
        public String username;
        public String realName;
        public String password;
        public UserRole role;
        public String phone;
        public Boolean enabled;
    }

    public static class UserResponse {
        public Long id;
        public String username;
        public String realName;
        public UserRole role;
        public String roleLabel;
        public String phone;
        public Boolean enabled;
        public LocalDateTime lastLoginAt;
        public LocalDateTime createdAt;
        public LocalDateTime updatedAt;
    }

    public static class InventorySaveRequest {
        public Long id;
        public String warehouseCode;
        public String locationCode;
        public String warehouseMaterialCode;
        public String materialCode;
        public String materialName;
        public BigDecimal stockQty;
        public BigDecimal safetyStock;
        public String batchNo;
        public Boolean frozen;
        public String freezeReason;
        public String remark;
    }

    public static class ConfigRequest {
        public Long id;
        public String configKey;
        public String configName;
        public String configValue;
        public String remark;
        public Boolean editable;
    }
}
