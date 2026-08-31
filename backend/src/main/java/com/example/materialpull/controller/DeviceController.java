package com.example.materialpull.controller;

import com.example.materialpull.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/device")
@RequiredArgsConstructor
public class DeviceController {

    /**
     * 安卓设备登记接口
     * 不做任何鉴权，仅记录设备信息
     */
    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody DeviceLoginRequest req) {
        log.info("设备登记: deviceNo={}, deviceModel={}, employeeNo={}, source={}, remark={}", 
            req.deviceNo, req.deviceModel, req.employeeNo, req.source, req.remark);
        
        return ApiResponse.ok(Map.of(
            "deviceNo", req.deviceNo,
            "message", "设备已登记",
            "timestamp", LocalDateTime.now()
        ));
    }

    public static class DeviceLoginRequest {
        public String deviceNo;
        public String deviceModel;
        public String employeeNo;
        public String source;
        public String remark;
    }
}
